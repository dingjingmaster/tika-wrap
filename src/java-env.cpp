//
// Created by dingjing on 2/11/25.
//

#include "java-env.h"

#include <jni.h>
#include <QFile>
#include <QMutex>
#include <QDebug>
#include <QFileInfo>

#include <wait.h>
#include <setjmp.h>
#include <unistd.h>

#include "macros/macros.h"

static sigjmp_buf gsJumpBuffer;

extern "C" void sigsegv_handler(int sig)
{
    siglongjmp(gsJumpBuffer, 1);
}

class JavaEnvPrivate
{
    Q_DECLARE_PUBLIC(JavaEnv);
public:
    bool initJvm();
    void closeJvm();

    bool autoParserParserFile(const QString &filePath, const QString& tmpDir) const;

    ~JavaEnvPrivate();
    explicit JavaEnvPrivate(JavaEnv* q);

private:
    QMutex                  mJvmLocker;
    JavaVM*                 mJvm = nullptr;
    JNIEnv*                 mJvmEnv = nullptr;

    jobject                 mFullParserObj = nullptr;
    jclass                  mFullParserClass = nullptr;

    jobject                 mAutoParserObj = nullptr;
    jclass                  mAutoParserClass = nullptr;
    jmethodID               mAutoParserParserFileMethod = nullptr;

    JavaEnv*                q_ptr = nullptr;
};

bool JavaEnvPrivate::initJvm()
{
    QMutexLocker locker(&mJvmLocker);
    // C_RETURN_VAL_IF_FAIL(mJvm && mJvmEnv, true);

    JavaVMInitArgs jvmArgs = {};
    JavaVMOption   jvmOptions[4] = {};

    jvmOptions[0].optionString = const_cast<char*>("-Djava.class.path=.:" INSTALL_PATH "/lib/tika-main-1.0.0.jar");
    jvmOptions[1].optionString = const_cast<char*>("-Xms1G");
    jvmOptions[2].optionString = const_cast<char*>("-Xmx1G");
    jvmOptions[3].optionString = const_cast<char*>("-XX:PermSize=2G");
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

    return true;
}

void JavaEnvPrivate::closeJvm()
{
    QMutexLocker locker(&mJvmLocker);
    if (mJvm) {
        mJvm->DestroyJavaVM();
        mJvm = nullptr;

        mFullParserObj = nullptr;
        mFullParserClass = nullptr;

        mAutoParserObj = nullptr;
        mAutoParserClass = nullptr;
    }
}

bool JavaEnvPrivate::autoParserParserFile(const QString & filePath, const QString& tmpDir) const
{
    const QFileInfo tFi(tmpDir);
    C_RETURN_VAL_IF_FAIL(QFile::exists(filePath) && tFi.exists() && tFi.isDir(), false);

    if (!mJvm) {
        qWarning() << "JVM not initialized 1!";
        return false;
    }

    if (!mJvmEnv) {
        qWarning() << "JVM not initialized 2!";
        return false;
    }

    bool ret = false;
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
}

JavaEnvPrivate::~JavaEnvPrivate()
{
    closeJvm();
}

JavaEnvPrivate::JavaEnvPrivate(JavaEnv * q)
    : q_ptr(q)
{
}

JavaEnv JavaEnv::gInstance;

JavaEnv* JavaEnv::getInstance()
{
    return &JavaEnv::gInstance;
}

bool JavaEnv::parseFile(const QString & absFilePath, const QString& tmpDir)
{
    Q_D(JavaEnv);

    d->mJvm->AttachCurrentThread(reinterpret_cast<void**>(&(d->mJvmEnv)), nullptr);
    const bool ret = d->autoParserParserFile(absFilePath, tmpDir);
    d->mJvm->DetachCurrentThread();

    return ret;
}

JavaEnv::JavaEnv()
    : QObject(nullptr), d_ptr(new JavaEnvPrivate(this))
{
    Q_D(JavaEnv);

    if (!d->initJvm()) {
        qWarning() << "JavaEnv init failed!";
        d->closeJvm();
    }
}

JavaEnv::JavaEnv(const JavaEnv &)
{

}

JavaEnv * JavaEnv::operator=(const JavaEnv &) const
{
    return nullptr;
}
