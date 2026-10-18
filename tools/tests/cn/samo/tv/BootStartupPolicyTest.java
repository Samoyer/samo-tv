package cn.samo.tv;

/** Host tests run against the production policy, without an Android runtime. */
public final class BootStartupPolicyTest {
    private static final int BOOT=140;
    private static final BootStartupPolicy.Source BROADCAST=BootStartupPolicy.Source.BOOT_BROADCAST;
    private static final BootStartupPolicy.Source BIND=BootStartupPolicy.Source.SERVICE_CONNECTED;
    private static int checks;

    public static void main(String[] args) {
        missingAndRevokedAuthorization();
        optedOut();
        unknownBoot();
        lateBindWithinWindow();
        oldBootUptime();
        windowBoundary();
        staleLedger();
        actualResumeSuppressesStartup();
        broadcastRequestStillAllowsBinding();
        duplicateBroadcast();
        rebindAcrossProcessRestart();
        bindingThenBroadcast();
        newBootResetsClaims();
        restoredAuthorization();
        manualResumeBeforeBroadcast();
        System.out.println("PASS: "+checks+" startup policy assertions (15 scenarios)");
    }

    private static BootStartupPolicy.State empty() {
        return new BootStartupPolicy.State(-1,false,false,false);
    }

    private static BootStartupPolicy.Decision evaluate(BootStartupPolicy.Source source,long uptime,
            BootStartupPolicy.State state) {
        return BootStartupPolicy.evaluate(source,true,true,BOOT,uptime,state);
    }

    private static void expect(String name,Object actual,Object expected) {
        if(!expected.equals(actual))throw new AssertionError(name+": expected "+expected+", got "+actual);
        checks++;
    }

    private static void missingAndRevokedAuthorization() {
        for(BootStartupPolicy.Source source:BootStartupPolicy.Source.values()) {
            expect("missing authorization "+source,
                BootStartupPolicy.evaluate(source,true,false,BOOT,40_000,empty()),
                BootStartupPolicy.Decision.NOT_AUTHORIZED);
            expect("revoked authorization "+source,
                BootStartupPolicy.evaluate(source,true,false,BOOT,45_000,empty().claim(BOOT,BROADCAST)),
                BootStartupPolicy.Decision.NOT_AUTHORIZED);
        }
    }

    private static void optedOut() {
        for(BootStartupPolicy.Source source:BootStartupPolicy.Source.values())
            expect("opt-out "+source,BootStartupPolicy.evaluate(source,false,true,BOOT,20_000,empty()),
                BootStartupPolicy.Decision.OPTED_OUT);
    }

    private static void unknownBoot() {
        for(BootStartupPolicy.Source source:BootStartupPolicy.Source.values())
            expect("unknown boot "+source,BootStartupPolicy.evaluate(source,true,true,-1,20_000,empty()),
                BootStartupPolicy.Decision.UNKNOWN_BOOT);
    }

    private static void lateBindWithinWindow() {
        expect("late bind before cutoff",evaluate(BIND,119_999,empty()),BootStartupPolicy.Decision.REQUEST_LAUNCH);
    }

    private static void oldBootUptime() {
        for(BootStartupPolicy.Source source:BootStartupPolicy.Source.values())
            expect("ordinary later bind/broadcast "+source,evaluate(source,23_800_000,empty()),
                BootStartupPolicy.Decision.OUTSIDE_BOOT_WINDOW);
    }

    private static void windowBoundary() {
        for(BootStartupPolicy.Source source:BootStartupPolicy.Source.values()) {
            expect("at cutoff "+source,evaluate(source,BootStartupPolicy.BOOT_WINDOW_MS,empty()),
                BootStartupPolicy.Decision.OUTSIDE_BOOT_WINDOW);
            expect("invalid uptime "+source,evaluate(source,-1,empty()),
                BootStartupPolicy.Decision.OUTSIDE_BOOT_WINDOW);
            expect("early uptime "+source,evaluate(source,0,empty()),
                BootStartupPolicy.Decision.REQUEST_LAUNCH);
        }
    }

    private static void staleLedger() {
        BootStartupPolicy.State previous=new BootStartupPolicy.State(BOOT-1,true,true,true);
        expect("previous boot resume does not suppress bind",evaluate(BIND,35_000,previous),
            BootStartupPolicy.Decision.REQUEST_LAUNCH);
        expect("previous boot claims do not suppress broadcast",evaluate(BROADCAST,35_000,previous),
            BootStartupPolicy.Decision.REQUEST_LAUNCH);
    }

    private static void actualResumeSuppressesStartup() {
        BootStartupPolicy.State resumed=empty().acknowledgeResume(BOOT);
        for(BootStartupPolicy.Source source:BootStartupPolicy.Source.values())
            expect("actual resume suppresses "+source,evaluate(source,55_000,resumed),
                BootStartupPolicy.Decision.ALREADY_RESUMED);
    }

    private static void broadcastRequestStillAllowsBinding() {
        BootStartupPolicy.State requested=empty().claim(BOOT,BROADCAST);
        expect("requested is not resumed",requested.resumed,false);
        expect("broadcast claimed",requested.broadcastAttempted,true);
        expect("binding still unclaimed",requested.bindingAttempted,false);
        expect("broadcast launch request permits one compensation",evaluate(BIND,65_000,requested),
            BootStartupPolicy.Decision.REQUEST_LAUNCH);
        BootStartupPolicy.State compensated=requested.claim(BOOT,BIND);
        expect("both requests remain distinct from resume",compensated.resumed,false);
        expect("bind deduplicated after compensation",evaluate(BIND,66_000,compensated),
            BootStartupPolicy.Decision.ALREADY_ATTEMPTED);
        expect("resume receipt after launch",compensated.acknowledgeResume(BOOT).resumed,true);
    }

    private static void duplicateBroadcast() {
        BootStartupPolicy.State requested=empty().claim(BOOT,BROADCAST);
        expect("duplicate broadcast",evaluate(BROADCAST,30_000,requested),
            BootStartupPolicy.Decision.ALREADY_ATTEMPTED);
    }

    private static void rebindAcrossProcessRestart() {
        BootStartupPolicy.State attempted=empty().claim(BOOT,BIND);
        // Reconstruct the exact persisted fields, rather than relying on object identity.
        BootStartupPolicy.State restored=new BootStartupPolicy.State(attempted.bootCount,
            attempted.broadcastAttempted,attempted.bindingAttempted,attempted.resumed);
        expect("process restart/rebind",evaluate(BIND,50_000,restored),
            BootStartupPolicy.Decision.ALREADY_ATTEMPTED);
        expect("unacknowledged launch is not accepted",restored.resumed,false);
    }

    private static void bindingThenBroadcast() {
        BootStartupPolicy.State bound=empty().claim(BOOT,BIND);
        expect("one broadcast after binding",evaluate(BROADCAST,51_000,bound),
            BootStartupPolicy.Decision.REQUEST_LAUNCH);
        BootStartupPolicy.State both=bound.claim(BOOT,BROADCAST);
        expect("bind claim retained",both.bindingAttempted,true);
        expect("broadcast claim retained",both.broadcastAttempted,true);
        for(BootStartupPolicy.Source source:BootStartupPolicy.Source.values())
            expect("at most two sources "+source,evaluate(source,52_000,both),
                BootStartupPolicy.Decision.ALREADY_ATTEMPTED);
    }

    private static void newBootResetsClaims() {
        BootStartupPolicy.State old=new BootStartupPolicy.State(BOOT-1,true,true,true);
        BootStartupPolicy.State claimed=old.claim(BOOT,BIND);
        expect("new boot identity",claimed.bootCount,BOOT);
        expect("old broadcast claim cleared",claimed.broadcastAttempted,false);
        expect("new bind claim saved",claimed.bindingAttempted,true);
        expect("old completion cleared",claimed.resumed,false);
    }

    private static void restoredAuthorization() {
        expect("restored authorization permits still-unclaimed bind",evaluate(BIND,60_000,empty()),
            BootStartupPolicy.Decision.REQUEST_LAUNCH);
        expect("late restoration never steals foreground",evaluate(BIND,120_001,empty()),
            BootStartupPolicy.Decision.OUTSIDE_BOOT_WINDOW);
    }

    private static void manualResumeBeforeBroadcast() {
        BootStartupPolicy.State resumed=empty().acknowledgeResume(BOOT);
        expect("manual resume has no fabricated request",resumed.broadcastAttempted,false);
        expect("manual resume blocks broadcast",evaluate(BROADCAST,20_000,resumed),
            BootStartupPolicy.Decision.ALREADY_RESUMED);
        BootStartupPolicy.State newResume=new BootStartupPolicy.State(BOOT-1,true,true,true).acknowledgeResume(BOOT);
        expect("new boot manual resume clears old binding",newResume.bindingAttempted,false);
        expect("new boot manual resume acknowledged",newResume.resumed,true);
    }
}
