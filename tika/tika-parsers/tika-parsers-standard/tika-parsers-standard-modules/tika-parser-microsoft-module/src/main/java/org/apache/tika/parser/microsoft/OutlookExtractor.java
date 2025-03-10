/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.microsoft;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.poi.hmef.attribute.MAPIRtfAttribute;
import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.datatypes.AttachmentChunks;
import org.apache.poi.hsmf.datatypes.ByteChunk;
import org.apache.poi.hsmf.datatypes.Chunk;
import org.apache.poi.hsmf.datatypes.Chunks;
import org.apache.poi.hsmf.datatypes.MAPIProperty;
import org.apache.poi.hsmf.datatypes.PropertyValue;
import org.apache.poi.hsmf.datatypes.RecipientChunks;
import org.apache.poi.hsmf.datatypes.StringChunk;
import org.apache.poi.hsmf.datatypes.Types;
import org.apache.poi.hsmf.exceptions.ChunkNotFoundException;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.util.CodePageUtil;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.HtmlEncodingDetector;
import org.apache.tika.parser.html.JSoupParser;
import org.apache.tika.parser.mailcommons.MailDateParser;
import org.apache.tika.parser.microsoft.rtf.RTFParser;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Outlook Message Parser.
 */
public class OutlookExtractor extends AbstractPOIFSExtractor {

    private static final Metadata EMPTY_METADATA = new Metadata();

    private static Pattern HEADER_KEY_PAT =
            Pattern.compile("\\A([\\x21-\\x39\\x3B-\\x7E]+):(.*?)\\Z");

    private final MAPIMessage msg;
    //this according to the spec; in practice, it is probably more likely
    //that a "split field" fails to start with a space character than
    //that a real header contains anything but [-_A-Za-z0-9].
    //e.g.
    //header: this header goes onto the next line
    //<mailto:xyz@cnn.com...
    private final ParseContext parseContext;
    private final boolean extractAllAlternatives;
    HtmlEncodingDetector detector = new HtmlEncodingDetector();


    public OutlookExtractor(DirectoryNode root, Metadata metadata, ParseContext context) throws TikaException {
        super(context, metadata);
        this.parseContext = context;
        this.extractAllAlternatives =
                context.get(OfficeParserConfig.class).isExtractAllAlternativesFromMSG();
        try {
            this.msg = new MAPIMessage(root);
        } catch (IOException e) {
            throw new TikaException("Failed to parse Outlook message", e);
        }
    }

    //need to add empty string to ensure that parallel arrays are parallel
    //even if one value is null.
    public static void addEvenIfNull(Property property, String value, Metadata metadata) {
        if (value == null) {
            value = "";
        }
        metadata.add(property, value);
    }

    private static void setFirstChunk(List<Chunk> chunks, Property property, Metadata metadata) {
        if (chunks == null || chunks.size() < 1 || chunks.get(0) == null) {
            return;
        }
        metadata.set(property, chunks.get(0).toString());
    }

    private static void addFirstChunk(List<Chunk> chunks, Property property, Metadata metadata) {
        if (chunks == null || chunks.size() < 1 || chunks.get(0) == null) {
            return;
        }
        metadata.add(property, chunks.get(0).toString());
    }

    //Still needed by PSTParser
    public static String getMessageClass(String messageClass) {
        if (messageClass == null || messageClass.trim().length() == 0) {
            return "UNSPECIFIED";
        } else if (messageClass.equalsIgnoreCase("IPM.Note")) {
            return "NOTE";
        } else if (messageClass.equalsIgnoreCase("IPM.Contact")) {
            return "CONTACT";
        } else if (messageClass.equalsIgnoreCase("IPM.Appointment")) {
            return "APPOINTMENT";
        } else if (messageClass.equalsIgnoreCase("IPM.StickyNote")) {
            return "STICKY_NOTE";
        } else if (messageClass.equalsIgnoreCase("IPM.Task")) {
            return "TASK";
        } else if (messageClass.equalsIgnoreCase("IPM.Post")) {
            return "POST";
        } else {
            return "UNKNOWN";
        }
    }

    public void parse(XHTMLContentHandler xhtml)
            throws TikaException, SAXException, IOException {
        try {
            msg.setReturnNullOnMissingChunk(true);

            try {
                parentMetadata.set(Office.MAPI_MESSAGE_CLASS, msg.getMessageClassEnum().name());
            } catch (ChunkNotFoundException e) {
                //swallow
            }

            // If the message contains strings that aren't stored
            //  as Unicode, try to sort out an encoding for them
            if (msg.has7BitEncodingStrings()) {
                guess7BitEncoding(msg);
            }

            // Start with the metadata
            String subject = msg.getSubject();
            Map<String, String[]> headers = normalizeHeaders(msg.getHeaders());
            String from = msg.getDisplayFrom();

            handleFromTo(headers, parentMetadata);

            parentMetadata.set(TikaCoreProperties.TITLE, subject);
            parentMetadata.set(TikaCoreProperties.SUBJECT, msg.getConversationTopic());
            parentMetadata.set(TikaCoreProperties.DESCRIPTION, msg.getConversationTopic());

            try {
                for (String recipientAddress : msg.getRecipientEmailAddressList()) {
                    if (recipientAddress != null) {
                        parentMetadata.add(Metadata.MESSAGE_RECIPIENT_ADDRESS, recipientAddress);
                    }
                }
            } catch (ChunkNotFoundException he) {
                // Will be fixed in POI 3.7 Final
            }

            for (Map.Entry<String, String[]> e : headers.entrySet()) {
                String headerKey = e.getKey();
                for (String headerValue : e.getValue()) {
                    parentMetadata.add(Metadata.MESSAGE_RAW_HEADER_PREFIX + headerKey, headerValue);
                }
            }

            // Date - try two ways to find it
            // First try via the proper chunk
            if (msg.getMessageDate() != null) {
                parentMetadata.set(TikaCoreProperties.CREATED, msg.getMessageDate().getTime());
                parentMetadata.set(TikaCoreProperties.MODIFIED, msg.getMessageDate().getTime());
            } else {
                if (headers != null && headers.size() > 0) {
                    for (Map.Entry<String, String[]> header : headers.entrySet()) {
                        String headerKey = header.getKey();
                        if (headerKey.toLowerCase(Locale.ROOT).startsWith("date:")) {
                            String date = headerKey.substring(headerKey.indexOf(':') + 1).trim();

                            // See if we can parse it as a normal mail date
                            try {
                                Date d = MailDateParser.parseDateLenient(date);
                                parentMetadata.set(TikaCoreProperties.CREATED, d);
                                parentMetadata.set(TikaCoreProperties.MODIFIED, d);
                            } catch (SecurityException e ) {
                                throw e;
                            } catch (Exception e) {
                                // Store it as-is, and hope for the best...
                                parentMetadata.set(TikaCoreProperties.CREATED, date);
                                parentMetadata.set(TikaCoreProperties.MODIFIED, date);
                            }
                            break;
                        }
                    }
                }
            }

            writeSelectHeadersInBody(subject, from, msg, xhtml);

            // Get the message body. Preference order is: html, rtf, text
            Chunk htmlChunk = null;
            Chunk rtfChunk = null;
            Chunk textChunk = null;
            for (Chunk chunk : msg.getMainChunks().getChunks()) {
                if (chunk.getChunkId() == MAPIProperty.BODY_HTML.id) {
                    htmlChunk = chunk;
                }
                if (chunk.getChunkId() == MAPIProperty.RTF_COMPRESSED.id) {
                    rtfChunk = chunk;
                }
                if (chunk.getChunkId() == MAPIProperty.BODY.id) {
                    textChunk = chunk;
                }
            }
            handleBodyChunks(htmlChunk, rtfChunk, textChunk, xhtml);

            // Process the attachments
            for (AttachmentChunks attachment : msg.getAttachmentFiles()) {

                String filename = null;
                if (attachment.getAttachLongFileName() != null) {
                    filename = attachment.getAttachLongFileName().getValue();
                } else if (attachment.getAttachFileName() != null) {
                    filename = attachment.getAttachFileName().getValue();
                }

                if (attachment.getAttachData() != null) {
                    handleEmbeddedResource(
                            TikaInputStream.get(attachment.getAttachData().getValue()), filename,
                            null, null, xhtml, true);
                }
                if (attachment.getAttachmentDirectory() != null) {
                    handleEmbeddedOfficeDoc(attachment.getAttachmentDirectory().getDirectory(), filename,
                            xhtml, true);
                }
            }
        } catch (ChunkNotFoundException e) {
            throw new TikaException("POI MAPIMessage broken - didn't return null on missing chunk",
                    e);
        } finally {
            //You'd think you'd want to call msg.close().
            //Don't do that.  That closes down the file system.
            //If an msg has multiple msg attachments, some of them
            //can reside in the same file system.  After the first
            //child is read, the fs is closed, and the other children
            //get a java.nio.channels.ClosedChannelException
        }
    }

    private void writeSelectHeadersInBody(String subject, String from, MAPIMessage msg, XHTMLContentHandler xhtml)
            throws SAXException, ChunkNotFoundException {
        if (! officeParserConfig.isWriteSelectHeadersInBody()) {
            return;
        }
        xhtml.element("h1", subject);

        // Output the from and to details in text, as you
        //  often want them in text form for searching
        xhtml.startElement("dl");
        if (from != null) {
            header(xhtml, "From", from);
        }
        header(xhtml, "To", msg.getDisplayTo());
        header(xhtml, "Cc", msg.getDisplayCC());
        header(xhtml, "Bcc", msg.getDisplayBCC());
        try {
            header(xhtml, "Recipients", msg.getRecipientEmailAddress());
        } catch (ChunkNotFoundException e) {
            //swallow
        }
        xhtml.endElement("dl");

    }

    private void handleBodyChunks(Chunk htmlChunk, Chunk rtfChunk, Chunk textChunk,
                                  XHTMLContentHandler xhtml)
            throws SAXException, IOException, TikaException {

        if (extractAllAlternatives) {
            extractAllAlternatives(htmlChunk, rtfChunk, textChunk, xhtml);
            return;
        }
        if (officeParserConfig.isWriteSelectHeadersInBody()) {
            xhtml.startElement("div", "class", "message-body");
            _handleBodyChunks(htmlChunk, rtfChunk, textChunk, xhtml);
            xhtml.endElement("div");
        } else {
            _handleBodyChunks(htmlChunk, rtfChunk, textChunk, xhtml);
        }
    }
    private void _handleBodyChunks(Chunk htmlChunk, Chunk rtfChunk, Chunk textChunk,
                                  XHTMLContentHandler xhtml)
            throws SAXException, IOException, TikaException {
        boolean doneBody = false;
        if (htmlChunk != null) {
            byte[] data = null;
            if (htmlChunk instanceof ByteChunk) {
                data = ((ByteChunk) htmlChunk).getValue();
            } else if (htmlChunk instanceof StringChunk) {
                data = ((StringChunk) htmlChunk).getRawValue();
            }
            if (data != null) {
                Parser htmlParser = EmbeddedDocumentUtil
                        .tryToFindExistingLeafParser(JSoupParser.class, parseContext);
                if (htmlParser == null) {
                    htmlParser = new JSoupParser();
                }
                htmlParser.parse(UnsynchronizedByteArrayInputStream.builder().setByteArray(data).get(),
                        new EmbeddedContentHandler(new BodyContentHandler(xhtml)), new Metadata(),
                        parseContext);
                doneBody = true;
            }
        }
        if (rtfChunk != null && (extractAllAlternatives || !doneBody)) {
            ByteChunk chunk = (ByteChunk) rtfChunk;
            //avoid buffer underflow TIKA-2530
            //TODO -- would be good to find an example triggering file and
            //figure out if this is a bug in POI or a genuine 0 length chunk
            if (chunk.getValue() != null && chunk.getValue().length > 0) {
                MAPIRtfAttribute rtf =
                        new MAPIRtfAttribute(MAPIProperty.RTF_COMPRESSED, Types.BINARY.getId(),
                                chunk.getValue());
                RTFParser rtfParser = (RTFParser) EmbeddedDocumentUtil
                        .tryToFindExistingLeafParser(RTFParser.class, parseContext);
                if (rtfParser == null) {
                    rtfParser = new RTFParser();
                }
                rtfParser.parseInline(UnsynchronizedByteArrayInputStream.builder().setByteArray(rtf.getData()).get(),
                        xhtml, new Metadata(), parseContext);
                doneBody = true;
            }
        }
        if (textChunk != null && (extractAllAlternatives || !doneBody)) {
            xhtml.element("p", ((StringChunk) textChunk).getValue());
        }

    }

    private void extractAllAlternatives(Chunk htmlChunk, Chunk rtfChunk, Chunk textChunk,
                                        XHTMLContentHandler xhtml)
            throws TikaException, SAXException, IOException {
        if (htmlChunk != null) {
            byte[] data = getValue(htmlChunk);
            if (data != null) {
                handleEmbeddedResource(TikaInputStream.get(data), "html-body", null,
                        MediaType.TEXT_HTML.toString(), xhtml, true);
            }
        }
        if (rtfChunk != null) {
            ByteChunk chunk = (ByteChunk) rtfChunk;
            MAPIRtfAttribute rtf =
                    new MAPIRtfAttribute(MAPIProperty.RTF_COMPRESSED, Types.BINARY.getId(),
                            chunk.getValue());

            byte[] data = rtf.getData();
            if (data != null) {
                handleEmbeddedResource(TikaInputStream.get(data), "rtf-body", null,
                        "application/rtf", xhtml, true);
            }
        }
        if (textChunk != null) {
            byte[] data = getValue(textChunk);
            if (data != null) {
                Metadata chunkMetadata = new Metadata();
                chunkMetadata.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE,
                        MediaType.TEXT_PLAIN.toString());
                handleEmbeddedResource(TikaInputStream.get(data), chunkMetadata, null, "text-body",
                        null, MediaType.TEXT_PLAIN.toString(), xhtml, true);
            }
        }

    }

    //can return null!
    private byte[] getValue(Chunk chunk) {
        byte[] data = null;
        if (chunk instanceof ByteChunk) {
            data = ((ByteChunk) chunk).getValue();
        } else if (chunk instanceof StringChunk) {
            data = ((StringChunk) chunk).getRawValue();
        }
        return data;
    }

    private void handleFromTo(Map<String, String[]> headers, Metadata metadata)
            throws ChunkNotFoundException {
        String from = msg.getDisplayFrom();
        metadata.set(TikaCoreProperties.CREATOR, from);
        metadata.set(Metadata.MESSAGE_FROM, from);
        metadata.set(Metadata.MESSAGE_TO, msg.getDisplayTo());
        metadata.set(Metadata.MESSAGE_CC, msg.getDisplayCC());
        metadata.set(Metadata.MESSAGE_BCC, msg.getDisplayBCC());


        Chunks chunks = msg.getMainChunks();
        StringChunk sentByServerType = chunks.getSentByServerType();
        if (sentByServerType != null) {
            metadata.set(Office.MAPI_SENT_BY_SERVER_TYPE, sentByServerType.getValue());
        }

        Map<MAPIProperty, List<Chunk>> mainChunks = msg.getMainChunks().getAll();

        List<Chunk> senderAddresType = mainChunks.get(MAPIProperty.SENDER_ADDRTYPE);
        String senderAddressTypeString = "";
        if (senderAddresType != null && senderAddresType.size() > 0) {
            senderAddressTypeString = senderAddresType.get(0).toString();
        }

        //sometimes in SMTP .msg files there is an email in the sender name field.

        setFirstChunk(mainChunks.get(MAPIProperty.SENDER_NAME), Message.MESSAGE_FROM_NAME,
                metadata);
        setFirstChunk(mainChunks.get(MAPIProperty.SENT_REPRESENTING_NAME),
                Office.MAPI_FROM_REPRESENTING_NAME, metadata);

        setFirstChunk(mainChunks.get(MAPIProperty.SENDER_EMAIL_ADDRESS), Message.MESSAGE_FROM_EMAIL,
                metadata);
        setFirstChunk(mainChunks.get(MAPIProperty.SENT_REPRESENTING_EMAIL_ADDRESS),
                Office.MAPI_FROM_REPRESENTING_EMAIL, metadata);

        for (Recipient recipient : buildRecipients()) {
            switch (recipient.recipientType) {
                case TO:
                    addEvenIfNull(Message.MESSAGE_TO_NAME, recipient.name, metadata);
                    addEvenIfNull(Message.MESSAGE_TO_DISPLAY_NAME, recipient.displayName, metadata);
                    addEvenIfNull(Message.MESSAGE_TO_EMAIL, recipient.emailAddress, metadata);
                    break;
                case CC:
                    addEvenIfNull(Message.MESSAGE_CC_NAME, recipient.name, metadata);
                    addEvenIfNull(Message.MESSAGE_CC_DISPLAY_NAME, recipient.displayName, metadata);
                    addEvenIfNull(Message.MESSAGE_CC_EMAIL, recipient.emailAddress, metadata);
                    break;
                case BCC:
                    addEvenIfNull(Message.MESSAGE_BCC_NAME, recipient.name, metadata);
                    addEvenIfNull(Message.MESSAGE_BCC_DISPLAY_NAME, recipient.displayName,
                            metadata);
                    addEvenIfNull(Message.MESSAGE_BCC_EMAIL, recipient.emailAddress, metadata);
                    break;
                default:
                    //log unknown or undefined?
                    break;
            }
        }
    }

    //As of 3.15, POI currently returns header[] by splitting on /\r?\n/
    //this rebuilds headers that are broken up over several lines
    //this also decodes encoded headers.
    private Map<String, String[]> normalizeHeaders(String[] rows) {
        Map<String, String[]> ret = new LinkedHashMap<>();
        if (rows == null) {
            return ret;
        }
        StringBuilder sb = new StringBuilder();
        Map<String, List<String>> headers = new LinkedHashMap();
        Matcher headerKeyMatcher = HEADER_KEY_PAT.matcher("");
        String lastKey = null;
        int consec = 0;
        for (String row : rows) {
            headerKeyMatcher.reset(row);
            if (headerKeyMatcher.find()) {
                if (lastKey != null) {
                    List<String> vals = headers.get(lastKey);
                    vals = (vals == null) ? new ArrayList<>() : vals;
                    vals.add(decodeHeader(sb.toString()));
                    headers.put(lastKey, vals);
                }
                //reset sb
                sb.setLength(0);
                lastKey = headerKeyMatcher.group(1).trim();
                sb.append(headerKeyMatcher.group(2).trim());
                consec = 0;
            } else {
                if (consec > 0) {
                    sb.append("\n");
                }
                sb.append(row);
            }
            consec++;
        }

        //make sure to add the last value
        if (sb.length() > 0 && lastKey != null) {
            List<String> vals = headers.get(lastKey);
            vals = (vals == null) ? new ArrayList<>() : vals;
            vals.add(decodeHeader(sb.toString()));
            headers.put(lastKey, vals);
        }

        //convert to array
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            ret.put(e.getKey(), e.getValue().toArray(new String[0]));
        }
        return ret;

    }

    private String decodeHeader(String header) {
        return DecoderUtil.decodeEncodedWords(header, DecodeMonitor.SILENT);
    }

    private void header(XHTMLContentHandler xhtml, String key, String value) throws SAXException {
        if (value != null && value.length() > 0) {
            xhtml.element("dt", key);
            xhtml.element("dd", value);
        }
    }

    /**
     * Tries to identify the correct encoding for 7-bit (non-unicode)
     * strings in the file.
     * <p>Many messages store their strings as unicode, which is
     * nice and easy. Some use one-byte encodings for their
     * strings, but don't always store the encoding anywhere
     * helpful in the file.</p>
     * <p>This method checks for codepage properties, and failing that
     * looks at the headers for the message, and uses these to
     * guess the correct encoding for your file.</p>
     * <p>Bug #49441 has more on why this is needed</p>
     * <p>This is taken verbatim from POI (TIKA-1238)
     * as a temporary workaround to prevent unsupported encoding exceptions</p>
     */
    private void guess7BitEncoding(MAPIMessage msg) {
        Chunks mainChunks = msg.getMainChunks();
        //null check
        if (mainChunks == null) {
            return;
        }

        Map<MAPIProperty, List<PropertyValue>> props = mainChunks.getProperties();
        if (props != null) {
            // First choice is a codepage property
            for (MAPIProperty prop : new MAPIProperty[]{MAPIProperty.MESSAGE_CODEPAGE,
                    MAPIProperty.INTERNET_CPID}) {
                List<PropertyValue> val = props.get(prop);
                if (val != null && val.size() > 0) {
                    int codepage = ((PropertyValue.LongPropertyValue) val.get(0)).getValue();
                    String encoding = null;
                    try {
                        encoding = CodePageUtil.codepageToEncoding(codepage, true);
                    } catch (UnsupportedEncodingException e) {
                        //swallow
                    }
                    if (tryToSet7BitEncoding(msg, encoding)) {
                        return;
                    }
                }
            }
        }

        // Second choice is a charset on a content type header
        try {
            String[] headers = msg.getHeaders();
            if (headers != null && headers.length > 0) {
                // Look for a content type with a charset
                Pattern p = Pattern.compile("Content-Type:.*?charset=[\"']?([^;'\"]+)[\"']?",
                        Pattern.CASE_INSENSITIVE);

                for (String header : headers) {
                    if (header.startsWith("Content-Type")) {
                        Matcher m = p.matcher(header);
                        if (m.matches()) {
                            // Found it! Tell all the string chunks
                            String charset = m.group(1);
                            if (tryToSet7BitEncoding(msg, charset)) {
                                return;
                            }
                        }
                    }
                }
            }
        } catch (ChunkNotFoundException e) {
            //swallow
        }

        // Nothing suitable in the headers, try HTML
        // TODO: do we need to replicate this in Tika? If we wind up
        // parsing the html version of the email, this is duplicative??
        // Or do we need to reset the header strings based on the html
        // meta header if there is no other information?
        try {
            String html = msg.getHtmlBody();
            if (html != null && html.length() > 0) {
                Charset charset = null;
                try {
                    charset = detector.detect(UnsynchronizedByteArrayInputStream.builder().setByteArray(html.getBytes(UTF_8)).get(),
                            EMPTY_METADATA);
                } catch (IOException e) {
                    //swallow
                }
                if (charset != null && tryToSet7BitEncoding(msg, charset.name())) {
                    return;
                }
            }
        } catch (ChunkNotFoundException e) {
            //swallow
        }

        //absolute last resort, try charset detector
        StringChunk text = mainChunks.getTextBodyChunk();
        if (text != null) {
            CharsetDetector detector = new CharsetDetector();
            detector.setText(text.getRawValue());
            CharsetMatch match = detector.detect();
            if (match != null && match.getConfidence() > 35 &&
                    tryToSet7BitEncoding(msg, match.getName())) {
                return;
            }
        }
    }

    private boolean tryToSet7BitEncoding(MAPIMessage msg, String charsetName) {
        if (charsetName == null) {
            return false;
        }

        if (charsetName.equalsIgnoreCase("utf-8")) {
            return false;
        }
        try {
            if (Charset.isSupported(charsetName)) {
                msg.set7BitEncoding(charsetName);
                return true;
            }
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            //swallow
        }
        return false;
    }

    private List<Recipient> buildRecipients() {
        RecipientChunks[] recipientChunks = msg.getRecipientDetailsChunks();
        if (recipientChunks == null) {
            return Collections.EMPTY_LIST;
        }
        List<Recipient> recipients = new LinkedList<>();

        for (RecipientChunks chunks : recipientChunks) {
            Recipient r = new Recipient();
            r.displayName = (chunks.getRecipientDisplayNameChunk() != null) ?
                    chunks.getRecipientDisplayNameChunk().toString() : null;
            r.name = (chunks.getRecipientNameChunk() != null) ?
                    chunks.getRecipientNameChunk().toString() :
                    null;
            r.emailAddress = chunks.getRecipientEmailAddress();
            List<PropertyValue> vals = chunks.getProperties().get(MAPIProperty.RECIPIENT_TYPE);

            RECIPIENT_TYPE recipientType = RECIPIENT_TYPE.UNSPECIFIED;
            if (vals != null && vals.size() > 0) {
                Object val = vals.get(0).getValue();
                if (val instanceof Integer) {
                    recipientType = RECIPIENT_TYPE.getTypeFromVal((int) val);
                }
            }
            r.recipientType = recipientType;

            vals = chunks.getProperties().get(MAPIProperty.ADDRTYPE);
            if (vals != null && vals.size() > 0) {
                String val = vals.get(0).toString();
                if (val != null) {
                    val = val.toLowerCase(Locale.US);
                    //need to find example of this for testing
                    if (val.equals("ex")) {
                        r.addressType = ADDRESS_TYPE.EX;
                    } else if (val.equals("smtp")) {
                        r.addressType = ADDRESS_TYPE.SMTP;
                    }
                }
            }
            recipients.add(r);
        }
        return recipients;
    }

    public enum RECIPIENT_TYPE {
        TO(1), CC(2), BCC(3), UNRECOGNIZED(-1), UNSPECIFIED(-1);

        private final int val;

        RECIPIENT_TYPE(int val) {
            this.val = val;
        }

        public static RECIPIENT_TYPE getTypeFromVal(int val) {
            //mild hackery, clean up
            if (val > 0 && val < 4) {
                return RECIPIENT_TYPE.values()[val - 1];
            }
            return UNRECOGNIZED;
        }
    }


    private enum ADDRESS_TYPE {
        EX, SMTP
    }

    private static class Recipient {
        String name;
        String displayName;
        RECIPIENT_TYPE recipientType;
        String emailAddress;
        ADDRESS_TYPE addressType;
    }
}
