package com.kansen.fanfan.data.repository

/**
 * The server answered with something that could not be read, so the status
 * may well have been created. The text must not be kept for a blind resend.
 */
class StatusPostUnconfirmedException :
    RuntimeException("消息已发送，但未能确认内容，请在时间线查看")
