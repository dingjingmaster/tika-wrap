//
// Created by dingjing on 2/11/25.
//
#include "java-env.h"

#include <QDebug>
#include <QTemporaryDir>
#include <QCoreApplication>

int main (int argc, char* argv[])
{
    // QCoreApplication app(argc, argv);

    // JavaEnv::getInstance();

    QTemporaryDir tempDir;
    tempDir.setAutoRemove(false);
    // QString filePath = "/home/dingjing/TrayApp.zip";
    // QString filePath = "/home/dingjing/aa.zip";
    QString filePath = "/home/dingjing/tk.csv";
    QString tmpDir = tempDir.path();

    qInfo() << tmpDir;

    if (!JavaEnv::getInstance()->parseFile(filePath, tmpDir)) {
        qWarning() << "文件解析失败!";
    }

    QFile file(QString("%1/ctx.txt").arg(tmpDir));
    if (file.open(QIODevice::ReadOnly)) {
        qInfo() << "File content:\n" << file.readAll();
    }

    return 0;
}