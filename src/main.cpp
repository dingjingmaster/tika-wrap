//
// Created by dingjing on 2/11/25.
//
#include "java-env.h"
#include <QCoreApplication>

int main (int argc, char* argv[])
{
    QCoreApplication app(argc, argv);

    JavaEnv::getInstance();

    return 0;
}