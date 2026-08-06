package util

import java.io.File

internal expect fun atomicReplaceFile(source: File, target: File)
