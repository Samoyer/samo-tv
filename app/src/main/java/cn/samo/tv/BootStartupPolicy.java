package cn.samo.tv;

/** Pure, one-shot cold-boot rules; no timers, Android calls or foreground queries. */
final class BootStartupPolicy {
    static final long BOOT_WINDOW_MS=120_000L;

    enum Source { BOOT_BROADCAST, SERVICE_CONNECTED }
    enum Decision {
        REQUEST_LAUNCH, OPTED_OUT, NOT_AUTHORIZED, UNKNOWN_BOOT,
        OUTSIDE_BOOT_WINDOW, ALREADY_RESUMED, ALREADY_ATTEMPTED
    }

    static final class State {
        final int bootCount;
        final boolean broadcastAttempted;
        final boolean bindingAttempted;
        final boolean resumed;

        State(int bootCount,boolean broadcastAttempted,boolean bindingAttempted,boolean resumed) {
            this.bootCount=bootCount;
            this.broadcastAttempted=broadcastAttempted;
            this.bindingAttempted=bindingAttempted;
            this.resumed=resumed;
        }

        State claim(int currentBoot,Source source) {
            boolean same=bootCount==currentBoot;
            return new State(currentBoot,
                source==Source.BOOT_BROADCAST || (same && broadcastAttempted),
                source==Source.SERVICE_CONNECTED || (same && bindingAttempted),
                same && resumed);
        }

        State acknowledgeResume(int currentBoot) {
            boolean same=bootCount==currentBoot;
            return new State(currentBoot,same && broadcastAttempted,same && bindingAttempted,true);
        }
    }

    private BootStartupPolicy() {}

    static Decision evaluate(Source source,boolean requested,boolean authorized,
            int bootCount,long elapsedRealtime,State state) {
        if(!requested)return Decision.OPTED_OUT;
        if(!authorized)return Decision.NOT_AUTHORIZED;
        // No wall-clock estimate: an unavailable boot identity fails closed.
        if(bootCount<0)return Decision.UNKNOWN_BOOT;
        if(elapsedRealtime<0 || elapsedRealtime>=BOOT_WINDOW_MS)return Decision.OUTSIDE_BOOT_WINDOW;
        if(state.bootCount==bootCount) {
            if(state.resumed)return Decision.ALREADY_RESUMED;
            boolean attempted=source==Source.BOOT_BROADCAST?state.broadcastAttempted:state.bindingAttempted;
            if(attempted)return Decision.ALREADY_ATTEMPTED;
        }
        return Decision.REQUEST_LAUNCH;
    }
}
