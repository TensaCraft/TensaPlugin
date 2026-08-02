package ua.co.tensa.modules.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyChatNativeModeTest {

    @Test
    void nativeGlobalChatRequiresProxyChannelAndNativeMode() {
        assertThat(ProxyChatService.nativeGlobalChatEnabled(true, true, true)).isTrue();
        assertThat(ProxyChatService.nativeGlobalChatEnabled(false, true, true)).isFalse();
        assertThat(ProxyChatService.nativeGlobalChatEnabled(true, false, true)).isFalse();
        assertThat(ProxyChatService.nativeGlobalChatEnabled(true, true, false)).isFalse();
    }
}
