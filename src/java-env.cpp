//
// Created by dingjing on 2/11/25.
//

#include "java-env.h"

#include <jni.h>
#include <QDebug>


class JavaEnvPrivate
{
    Q_DECLARE_PUBLIC(JavaEnv);
public:
    bool initJvm();
    void closeJvm();
    explicit JavaEnvPrivate(JavaEnv* q);

private:
    JavaVM*                 mJvm = nullptr;
    JNIEnv*                 mJvmEnv = nullptr;
    JavaEnv*                q_ptr = nullptr;
};

bool JavaEnvPrivate::initJvm()
{
    JavaVMInitArgs jvmArgs = {};
    JavaVMOption   jvmOptions = {};

    jvmOptions.optionString = const_cast<char*>("-Djava.class.path=.:/data/code/tika-wrap/tmp-dir/tika-main-1.0.0.jar");
    jvmArgs.version = JNI_VERSION_1_8;
    jvmArgs.nOptions = 1;
    jvmArgs.options = &jvmOptions;
    jvmArgs.ignoreUnrecognized = false;

    if (JNI_OK != JNI_CreateJavaVM(&mJvm, reinterpret_cast<void**>(&mJvmEnv), &jvmArgs)) {
        qWarning() << "Create Java VM failed!";
        return false;
    }

    jclass fullParserClass = mJvmEnv->FindClass("com/github/dingjingmaster/tika/main/FileOperation/FullParser");
    if (nullptr == fullParserClass) {
        qWarning() << "Find class 'FullParser' failed!";
        return false;
    }

    return true;
}

void JavaEnvPrivate::closeJvm()
{
    if (mJvm) {
        mJvm->DestroyJavaVM();
        mJvm = nullptr;
    }
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

JavaEnv::JavaEnv()
    : QObject(nullptr), d_ptr(new JavaEnvPrivate(this))
{
    Q_D(JavaEnv);

    if (!d->initJvm()) {
        qWarning() << "JavaEnv init failed!";
    }
}

JavaEnv::JavaEnv(const JavaEnv &)
{

}

JavaEnv * JavaEnv::operator=(const JavaEnv &) const
{
    return nullptr;
}
