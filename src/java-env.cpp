//
// Created by dingjing on 2/11/25.
//

#include "java-env.h"

#include <QMimeType>
#include <QMimeDatabase>
#ifdef USE_TIKA_SERVER
#include <QTimer>
#include <QProcess>
#include <QEventLoop>
#include <QJsonObject>
#include <QJsonDocument>
#include <QNetworkReply>
#include <QHttpMultiPart>
#include <QProcessEnvironment>
#include <QNetworkAccessManager>
#else
#include <jni.h>
#include <setjmp.h>
#endif
#include <QFile>
#include <QMutex>
#include <QDebug>
#include <QDir>
#include <qeventloop.h>
#include <QFileInfo>
#include <QNetworkAccessManager>

#include <wait.h>
#include <unistd.h>

#include "macros/macros.h"

#ifndef USE_TIKA_SERVER
static sigjmp_buf gsJumpBuffer;

extern "C" void sigsegv_handler(int sig)
{
    siglongjmp(gsJumpBuffer, 1);
}
#endif

class JavaEnvPrivate
{
    Q_DECLARE_PUBLIC(JavaEnv);
public:
    bool initJvm();
    void closeJvm();

    bool autoParserParserFile(const QString &filePath, const QString& tmpDir);

    ~JavaEnvPrivate();
    explicit JavaEnvPrivate(JavaEnv* q);

private:
    QMutex                  mJvmLocker;
#ifdef USE_TIKA_SERVER
    QProcess*               mProcess{};

    void launchTikaServer ();
    std::atomic_bool        mAlreadyRunning{};
    QString                 mTikaServer = "/usr/local/andsec/scan/lib/tika-server.jar";
    qint32                  mTikaServerPort = 9999;
#else
    JavaVM*                 mJvm = nullptr;
    JNIEnv*                 mJvmEnv = nullptr;

    jobject                 mFullParserObj = nullptr;
    jclass                  mFullParserClass = nullptr;

    jobject                 mAutoParserObj = nullptr;
    jclass                  mAutoParserClass = nullptr;
    jmethodID               mAutoParserParserFileMethod = nullptr;
#endif

    JavaEnv*                q_ptr = nullptr;
};

bool JavaEnvPrivate::initJvm()
{
    QMutexLocker locker(&mJvmLocker);
#ifdef USE_TIKA_SERVER
    launchTikaServer();

#else
    JavaVMInitArgs jvmArgs = {};
    JavaVMOption   jvmOptions[7] = {};

    jvmOptions[0].optionString = const_cast<char*>("-Djava.class.path=.:" INSTALL_PATH "/lib/tika-main-1.0.0.jar");
    jvmOptions[1].optionString = const_cast<char*>("-Xms1G");
    jvmOptions[2].optionString = const_cast<char*>("-Xmx1G");
    jvmOptions[3].optionString = const_cast<char*>("-XX:PermSize=2G");
    jvmOptions[5].optionString = const_cast<char*>("-XX:+UseAltStack");
    jvmOptions[6].optionString = const_cast<char*>("-XX:+PrintGCDetails");
    jvmOptions[4].optionString = const_cast<char*>("-XX:+ShowMessageBoxOnError");
    jvmArgs.version = JNI_VERSION_1_8;
    jvmArgs.nOptions = 1;
    jvmArgs.options = jvmOptions;
    jvmArgs.ignoreUnrecognized = JNI_FALSE;

    if (JNI_OK != JNI_CreateJavaVM(&mJvm, reinterpret_cast<void**>(&mJvmEnv), &jvmArgs)) {
        qWarning() << "Create Java VM failed!";
        return false;
    }

    // if (!mJvmEnv || !mJvm) {
        // qWarning() << "Failed to create Java VM!";
        // return false;
    // }

    {
#if 0
        mFullParserClass = mJvmEnv->FindClass("com/github/dingjingmaster/tika/main/FileOperation/FullParser");
        if (nullptr == mFullParserClass) {
            qWarning() << "Find class 'FullParser' failed!";
            return false;
        }

        const jmethodID fullParser = mJvmEnv->GetMethodID(mFullParserClass, "<init>", "()V");
        if (!fullParser) {
            qWarning() << "Find method 'FullParser' failed!";
            return false;
        }

        mFullParserObj = mJvmEnv->NewObject(mFullParserClass, fullParser);
        if (nullptr == mFullParserObj) {
            qWarning() << "New object 'FullParser' failed!";
            return false;
        }
#endif
    }

    {
        mAutoParserClass = mJvmEnv->FindClass("com/github/dingjingmaster/tika/main/FileOperation/AutoParser");
        if (nullptr == mAutoParserClass) {
            mJvmEnv->ExceptionCheck();
            mJvmEnv->ExceptionDescribe();
            mJvmEnv->ExceptionClear();
            qWarning() << "Find class 'AutoParser' failed!";
            return false;
        }

        const jmethodID autoParser = mJvmEnv->GetMethodID(mAutoParserClass, "<init>", "()V");
        if (!autoParser) {
            qWarning() << "Find method 'AutoParser' failed!";
            return false;
        }

        mAutoParserObj = mJvmEnv->NewObject(mAutoParserClass, autoParser);
        if (nullptr == mAutoParserObj) {
            qWarning() << "New object 'AutoParser' failed!";
            return false;
        }

        mAutoParserParserFileMethod = mJvmEnv->GetMethodID(mAutoParserClass, "parserFile", "(Ljava/lang/String;Ljava/lang/String;)Z");
        if (nullptr == mAutoParserParserFileMethod) {
            qWarning() << "Find method 'AutoParser::parserFile' failed!";
            return false;
        }
    }
#endif

    return true;
}

void JavaEnvPrivate::closeJvm()
{
    QMutexLocker locker(&mJvmLocker);
#ifdef USE_TIKA_SERVER
    if (mProcess) {
        mProcess->kill();
        mProcess->waitForFinished();
        mProcess->deleteLater();
        mProcess = nullptr;
    }
#else
    if (mJvm) {
        mJvm->DestroyJavaVM();
        mJvm = nullptr;

        mFullParserObj = nullptr;
        mFullParserClass = nullptr;

        mAutoParserObj = nullptr;
        mAutoParserClass = nullptr;
    }
#endif
}

bool JavaEnvPrivate::autoParserParserFile(const QString& filePath, const QString& tmpDir)
{
    const QFileInfo tFi(tmpDir);
    C_RETURN_VAL_IF_FAIL(QFile::exists(filePath) && tFi.exists() && tFi.isDir(), false);

#ifdef USE_TIKA_SERVER
    qInfo() << "[TIKA] autoParserParserFile: " << filePath;
    if (!mAlreadyRunning) {
        initJvm();
        if (!mAlreadyRunning) {
            qWarning() << "Tika Server Not Running!";
            return false;
        }
    }

    const QDir dir(tmpDir);
    if (!dir.exists()) {
        if (!dir.mkpath(tmpDir)) {
            qWarning() << "Failed to create directory '" << tmpDir << "'!";
            return false;
        }
    }

    auto downloadData = [this] (const QString& localFile, const QString& uri, const QString& saveFile, bool isMeta = false) ->bool {
        QNetworkRequest request;
        QNetworkAccessManager http;
        QFileInfo fileInfo(localFile);
        http.setStrictTransportSecurityEnabled(false);

        QFile file (saveFile);
        if (!file.open(QFile::ReadWrite | QFile::Text)) {
            file.close();
            return false;
        }

        qInfo() << "file: " << localFile << ", uri: " << uri << ", saveFile: " << saveFile;
        QEventLoop loop;
        QTimer timer;

        timer.singleShot(1000 * 600 * 10, &loop, &QEventLoop::quit);

        auto getMimeType = [] (const QString& path) -> QString {
            const QMimeDatabase db;
            const QMimeType mimeType = db.mimeTypeForFile(path);
            const QString name = mimeType.name();
            if (nullptr == name || name.isEmpty()) {
                return "text/plain";
            }

            return "";
        };

        request.setUrl(uri);
        const auto metaType = getMimeType(localFile);
        if (!metaType.isEmpty()) {
            request.setHeader(QNetworkRequest::ContentTypeHeader, getMimeType(localFile));
        }
        request.setRawHeader("Host", QString("127.0.0.1:%1").arg(mTikaServerPort).toUtf8().data());
        request.setRawHeader("Accept", isMeta ? "application/json" : "text/plain; charset=UTF-8");

        QFile localF(localFile);
        if (!localF.open(QFile::ReadOnly)) {
            localF.close();
            file.close();
            return false;
        }

#if 0
        qInfo() << "header:";
        for (auto& h : request.rawHeaderList()) {
            qInfo() << h << ":"<< request.rawHeader(h);
        }
#endif

        QNetworkReply* reply = http.put(request, &localF);

        http.connect(reply, &QIODevice::readyRead, [&]() {
            const QByteArray data = reply->read(81920);
            file.write(data.constData(), data.length());
        });

        http.connect(reply, &QNetworkReply::finished, [&] () -> void {
            if (QNetworkReply::NoError == reply->error()) {
                file.flush();
                file.close();
            }
            else {
                qWarning() << "post error: " << reply->errorString() << ", code: " << reply->error();
            }
            loop.exit(0);
        });

        timer.start();
        loop.exec();

        file.close();
        localF.close();

        if (isMeta) {
            QFile metaFile(saveFile);
            if (metaFile.open(QFile::ReadWrite | QFile::Text)) {
                const QString bytes = metaFile.readAll();
                const QJsonDocument json = QJsonDocument::fromJson(bytes.toUtf8());
                metaFile.reset();
                if (json.isObject()) {
                    auto obj = json.object();
                    auto keys = obj.keys();
                    for (auto& k : keys) {
                        metaFile.write(QString("%1{]%2\n").arg(k).arg(obj[k].toString()).toUtf8());
                    }
                }
                metaFile.close();
            }
        }

        return true;
    };

    const QString ctxFile = QString("%1/ctx.txt").arg(tmpDir);
    const QString metaFile = QString("%1/meta.txt").arg(tmpDir);

    const QString ctxUrl = QString("http://127.0.0.1:%1/tika").arg(mTikaServerPort);
    const QString metaUrl = QString("http://127.0.0.1:%1/meta").arg(mTikaServerPort);

    if (!downloadData(filePath, metaUrl, metaFile, true)) {
        qWarning() << "Failed to parse file '" << filePath << "' meta info!";
        return false;
    }

    if (!downloadData(filePath, ctxUrl, ctxFile)) {
        qWarning() << "Failed to parse file '" << filePath << "' content!";
        return false;
    }

    return true;


#else
    qInfo() << "[JNI] autoParserParserFile: " << filePath;

    bool ret = false;

    if (!mJvm) {
        qWarning() << "JVM not initialized 1!";
        return false;
    }

    if (!mJvmEnv) {
        qWarning() << "JVM not initialized 2!";
        return false;
    }

    qInfo() << "Start autoParserParserFile";
    // const sighandler_t oldSegv = signal(SIGSEGV, sigsegv_handler);
    // if (sigsetjmp(gsJumpBuffer, 1) == 0) {
    try {
        jstring jFilePath = mJvmEnv->NewStringUTF(filePath.toUtf8().constData());
        jstring jTmpDir = mJvmEnv->NewStringUTF(tmpDir.toUtf8().constData());
        ret = (JNI_TRUE == mJvmEnv->CallBooleanMethod(mAutoParserObj, mAutoParserParserFileMethod, jFilePath, jTmpDir));
        mJvmEnv->ExceptionCheck();
        mJvmEnv->ExceptionDescribe();
        mJvmEnv->ExceptionClear();
    }
    catch (const std::exception& ex) {
        qWarning() << "error autoParserParserFile: " << ex.what();
    }
    // signal(SIGSEGV, oldSegv);
    qInfo() << "Finish autoParserParserFile";

    return ret;
#endif
}

JavaEnvPrivate::~JavaEnvPrivate()
{
    closeJvm();
}

JavaEnvPrivate::JavaEnvPrivate(JavaEnv * q)
    : q_ptr(q)
{
    mAlreadyRunning = false;
    initJvm();
}

// 已经上锁
void JavaEnvPrivate::launchTikaServer()
{
    Q_Q(JavaEnv);

    if (mProcess) {
        if (QProcess::NotRunning == mProcess->state()) {
            mAlreadyRunning = false;
            mProcess->deleteLater();
            mProcess = nullptr;
        }
    }

    if (!mProcess) {
        mProcess = new QProcess();
        QProcessEnvironment env = QProcessEnvironment::systemEnvironment();

        const QString ldLibraryPath = "/usr/local/andsec/scan/lib"
                                      ":/usr/local/andsec/scan/lib/java/lib"
                                      ":/usr/local/andsec/scan/lib/java/lib/jli"
                                      ":/usr/local/andsec/scan/lib/java/lib/server";

        env.insert("LD_PRELOAD", "");
        env.insert("LD_LIBRARY_PATH", ldLibraryPath);
        env.insert("PATH", "/usr/local/andsec/scan/bin:" + env.value("PATH"));
        mProcess->setProcessEnvironment(env);
        mProcess->setProgram("/usr/local/andsec/scan/bin/java");
        mProcess->setArguments(QStringList() << "-jar" << mTikaServer << "--port" << QString("%1").arg(mTikaServerPort));

#if 1
        qInfo() << "Start TikaServer: " << mProcess->program();
        qInfo() << "PATH: " << env.value("PATH");
        qInfo() << "LD_LIBRARY_PATH: " << env.value("LD_LIBRARY_PATH");
#endif

        QProcess::connect(mProcess, &QProcess::stateChanged, q_ptr, [&] (QProcess::ProcessState state) {
            switch (state) {
            default:
            case QProcess::NotRunning:
            case QProcess::Starting:
                mAlreadyRunning = false;
                break;
            case QProcess::Running:
                mAlreadyRunning = true;
                break;
            }
        });

        QTimer timer;
        QEventLoop loop;
#if 1
        QProcess::connect(mProcess, &QProcess::readyReadStandardOutput, [&] () {
            const QString info = mProcess->readAllStandardOutput();
            if (info.contains("http://") && info.contains(QString("%1").arg(mTikaServerPort))) {
                qInfo() << "TIKA Started!";
                loop.exit();
            }
            qInfo() << info;
        });
        QProcess::connect(mProcess, &QProcess::readyReadStandardError, [&] () {
            const QString info = mProcess->readAllStandardError();
            if (info.contains("http://") && info.contains(QString("%1").arg(mTikaServerPort))) {
                qInfo() << "TIKA Started!";
                loop.exit();
            }
            qWarning() << info;
        });
#else
        mProcess->setStandardErrorFile(QProcess::nullDevice());
        mProcess->setStandardOutputFile(QProcess::nullDevice());
#endif

        mProcess->start();
        mProcess->waitForStarted();

        timer.setSingleShot(true);
        timer.connect(&timer, &QTimer::timeout, [&] () {
            loop.exit();
        });
        timer.start(1000 * 300);
        loop.exec();
    }
}

JavaEnv* JavaEnv::gInstance = nullptr;

JavaEnv* JavaEnv::getInstance()
{
    if (!gInstance) {
        static QMutex lock;
        QMutexLocker locker(&lock);
        if (!gInstance) {
            gInstance = new JavaEnv();
        }
    }
    return gInstance;
}

bool JavaEnv::parseFile(const QString & absFilePath, const QString& tmpDir)
{
    Q_D(JavaEnv);

#ifdef USE_TIKA_SERVER
    const bool ret = d->autoParserParserFile(absFilePath, tmpDir);
#else
    d->mJvm->AttachCurrentThread(reinterpret_cast<void**>(&(d->mJvmEnv)), nullptr);
    const bool ret = d->autoParserParserFile(absFilePath, tmpDir);
    d->mJvm->DetachCurrentThread();
#endif

    return ret;
}

JavaEnv::JavaEnv() : QObject(nullptr), d_ptr(new JavaEnvPrivate(this))
{
    Q_D(JavaEnv);

#ifdef USE_TIKA_SERVER
#else
    if (!d->initJvm()) {
        qWarning() << "JavaEnv init failed!";
        d->closeJvm();
    }
#endif
}

JavaEnv::JavaEnv(const JavaEnv &)
{

}

JavaEnv * JavaEnv::operator=(const JavaEnv &) const
{
    return nullptr;
}
