package com.classroomadventure.folddisplay

/** Mac 側 Protocol.swift と対になる定義。全メッセージは [type:u8][length:u32 BE][payload]。 */
object Proto {
    const val PORT = 47800
    const val SERVICE_TYPE = "_folddisplay._tcp."

    const val HELLO = 0x01
    const val MOUSE = 0x20
    const val SCROLL = 0x21
    const val KEYFRAME = 0x30
    const val PING = 0x40

    const val CONFIG = 0x81
    const val VIDEO = 0x82
    const val CURSOR_POS = 0x83
    const val CURSOR_IMAGE = 0x84
    const val PONG = 0xC0

    const val FLAG_CODEC_CONFIG = 1
    const val FLAG_KEY_FRAME = 2

    const val M_MOVE = 0
    const val M_LEFT_DOWN = 1
    const val M_LEFT_UP = 2
    const val M_RIGHT_CLICK = 3
    const val M_LEFT_DRAG = 4
}
