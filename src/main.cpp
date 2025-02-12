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

    const char* files[] = {
        "/home/dingjing/TrayApp.zip",
        "/home/dingjing/aa.zip",
        "/home/dingjing/tk.csv",
        nullptr
    };

    for (int i = 0; files[i]; i++) {
        QTemporaryDir tempDir;
        // tempDir.setAutoRemove(false);
        QString tmpDir = tempDir.path();

        if (!JavaEnv::getInstance()->parseFile(files[i], tmpDir)) {
            qWarning() << files[i] << " 文件解析失败!";
        }

        QFile file(QString("%1/ctx.txt").arg(tmpDir));
        if (file.open(QIODevice::ReadOnly)) {
            qInfo() << "File content:\n" << file.readAll();
        }
    }

    return 0;
}