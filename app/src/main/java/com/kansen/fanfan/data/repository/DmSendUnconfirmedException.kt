package com.kansen.fanfan.data.repository

/**
 * The server accepted the message but the answer could not be read. The
 * message *was* delivered, so the caller must not offer a blind resend.
 */
class DmSendUnconfirmedException :
    RuntimeException("消息已发送，但未能确认内容，请打开会话查看")
