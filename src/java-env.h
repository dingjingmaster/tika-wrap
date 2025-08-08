//
// Created by dingjing on 2/11/25.
//

#ifndef tika_wrap_JAVA_ENV_H
#define tika_wrap_JAVA_ENV_H
#include <QObject>


class JavaEnvPrivate;
class JavaEnv final : public QObject
{
    Q_OBJECT
    Q_DECLARE_PRIVATE(JavaEnv)
public:
    static JavaEnv* getInstance();
    bool parseFile(const QString& absFilePath, const QString& tmpDir);

private:
    JavaEnv();
    JavaEnv(const JavaEnv&);
    JavaEnv* operator=(const JavaEnv&) const;

private:
    static JavaEnv*             gInstance;
    JavaEnvPrivate*             d_ptr = nullptr;
};



#endif // tika_wrap_JAVA_ENV_H
