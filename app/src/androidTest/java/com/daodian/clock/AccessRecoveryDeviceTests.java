package com.daodian.clock;

import android.content.Context;
import android.test.InstrumentationTestCase;
import com.daodian.clock.core.*;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.UUID;

public class AccessRecoveryDeviceTests extends InstrumentationTestCase {
    private Plan plan(){Plan p=new Plan("clockout");p.enabled=true;p.revision=2;return p;}
    private JSONObject failure(long now)throws Exception {
        return new JSONObject().put("id",UUID.randomUUID().toString()).put("kind","clockout").put("mode","REAL")
            .put("date",LocalDate.now(ScheduleRules.ZONE).toString()).put("trigger","定时").put("status","BLOCKED")
            .put("blockReason","ACCESS_DISCONNECTED").put("plannedAt",now-60000).put("startedAt",now).put("planRevision",2)
            .put("submitted",false).put("submissionPossible",false);
    }
    public void testRecoveryClaimIsDurableAndOnlyOnce()throws Exception {
        Context c=getInstrumentation().getTargetContext();c.deleteDatabase("verification-tests.db");
        long now=System.currentTimeMillis();Records db=Records.isolatedForTest(c);JSONObject r=failure(now);db.put(r);
        assertNotNull(db.accessRecovery(plan(),true,"REAL",now));assertNotNull(db.claimAccessRecovery(r.getString("id"),plan(),true,"REAL",now));
        db.close();db=Records.isolatedForTest(c);assertNull(db.accessRecovery(plan(),true,"REAL",now));
        assertNull(db.claimAccessRecovery(r.getString("id"),plan(),true,"REAL",now));db.close();
    }
    public void testLaterAttemptAndSubmissionGuardSuppressRecovery()throws Exception {
        Context c=getInstrumentation().getTargetContext();c.deleteDatabase("verification-tests.db");
        long now=System.currentTimeMillis();Records db=Records.isolatedForTest(c);JSONObject r=failure(now);db.put(r);
        JSONObject later=failure(now+1).put("trigger","手动").put("status","CHECK_REQUIRED");db.put(later);
        assertNull(db.accessRecovery(plan(),true,"REAL",now+1));db.close();
        c.deleteDatabase("verification-tests.db");db=Records.isolatedForTest(c);db.put(r);assertTrue(db.reserve(r));
        assertNull(db.accessRecovery(plan(),true,"REAL",now));db.close();
    }
    public void testLegacyFailureCannotBeReplayedAfterUpgrade()throws Exception {
        Context c=getInstrumentation().getTargetContext();c.deleteDatabase("verification-tests.db");
        long now=System.currentTimeMillis();Records db=Records.isolatedForTest(c);JSONObject r=failure(now);r.remove("planRevision");db.put(r);
        assertNull(db.accessRecovery(plan(),true,"REAL",now));db.close();
    }
}
