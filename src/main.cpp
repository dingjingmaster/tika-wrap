//
// Created by dingjing on 2/11/25.
//
#include "java-env.h"

#include <QDebug>
#include <QTemporaryDir>
#include <QCoreApplication>

int main (int argc, char* argv[])
{
    auto printFileContent = [&] (const char* filePath) -> void {
        QTemporaryDir tempDir;
        QString tmpDir = tempDir.path();

        if (!JavaEnv::getInstance()->parseFile(filePath, tmpDir)) {
            qWarning() << filePath << " 文件解析失败!";
        }

        QFile file(QString("%1/ctx.txt").arg(tmpDir));
        if (file.open(QIODevice::ReadOnly)) {
            qInfo() << "File content:\n" << file.readAll();
        }
    };

    if (argc == 1) {
        const char* files[] = {
            "/home/dingjing/TrayApp.zip",
            "/home/dingjing/aa.zip",
            "/home/dingjing/tk.csv",
            nullptr
        };
        for (int i = 0; files[i]; i++) {
            printFileContent(files[i]);
        }
    }
    else if (QFile::exists(argv[1])) {
        printFileContent(argv[1]);
    }

    return 0;
}