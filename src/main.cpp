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

        if (!JavaEnv().parseFile(filePath, tmpDir)) {
            qWarning() << filePath << " 文件解析失败!";
        }

        QFile file(QString("%1/ctx.txt").arg(tmpDir));
        if (file.open(QIODevice::ReadOnly)) {
            qInfo() << "File content:\n" << QString(file.readAll());
        }
        file.close();

        QFile fileM(QString("%1/meta.txt").arg(tmpDir));
        if (fileM.open(QIODevice::ReadOnly)) {
            auto lines = QString(fileM.readAll()).split("\n");
            for (auto& line : lines) {
                auto arr = QString(line).split("{]");
                if (arr.size() != 2) {
                    continue;
                }
                qInfo() << arr.at(0) << ":" << arr.at(1);
            }
        }
        fileM.close();
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