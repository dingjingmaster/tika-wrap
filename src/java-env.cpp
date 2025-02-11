//
// Created by dingjing on 2/11/25.
//

#include "java-env.h"

#include <jni.h>


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

    jvmOptions.optionString = const_cast<char*>("-Djava.class.path=.");
    jvmArgs.version = JNI_VERSION_1_8;
    jvmArgs.nOptions = 1;
    jvmArgs.options = &jvmOptions;
    jvmArgs.ignoreUnrecognized = false;

    if (JNI_OK != JNI_CreateJavaVM(&mJvm, reinterpret_cast<void**>(&mJvmEnv), &jvmArgs)) {
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
}

JavaEnv::JavaEnv(const JavaEnv &)
{

}

JavaEnv * JavaEnv::operator=(const JavaEnv &) const
{
    return nullptr;
}
