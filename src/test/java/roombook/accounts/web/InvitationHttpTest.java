package roombook.accounts.web;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import roombook.accounts.application.*;
import roombook.kernel.*;
import roombook.testsupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("TS-10") @Tag("SEC-03") @Tag("SEC-11") @Tag("FR-23")
@ExtendWith(OutputCaptureExtension.class)
class InvitationHttpTest extends HttpTestSupport {
    @Autowired InvitationService invitations;
    @Autowired InvitationRedemption redemption;
    private static final String PASSWORD = "Invite-Password-2026";
    private Actor actor() { return new Actor(adminId, Role.ADMIN); }
    private Session anonymous() throws Exception {
        var response = api("GET", "/api/v1/csrf", null, "");
        return new Session(response.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0],json(response).get("token").asString());
    }
    private java.net.http.HttpResponse<String> accept(String token, String password, String confirmation, Session session) throws Exception {
        return send("POST", "/join", session, "token="+query(token)+"&password="+query(password)+"&confirmation="+query(confirmation), "application/x-www-form-urlencoded");
    }
    @Test void adminIssuesEmployeeAcceptsAndCanSignInExactlyOnce(CapturedOutput output) throws Exception {
        var issued = send("POST", "/admin/invitations", admin, "username=new.member&displayName="+query("<Нова людина>"), "application/x-www-form-urlencoded");
        assertEquals(200, issued.statusCode(), issued.body());
        var match = Pattern.compile("value=\"/join#([A-Za-z0-9_-]{43})\"").matcher(issued.body()); assertTrue(match.find(), issued.body());
        String token = match.group(1);
        assertFalse(issued.body().contains("<Нова людина>"));
        assertTrue(issued.body().contains("&lt;Нова людина&gt;"));
        assertTrue(issued.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
        assertFalse(api("GET", "/admin/invitations", admin, "").body().contains(token));
        try(var connection=PostgresContainerHolder.getOwnerConnection(); var statement=connection.createStatement(); var rows=statement.executeQuery("select token_hash from account_invitation")) {
            assertTrue(rows.next()); assertEquals(64,rows.getString(1).length()); assertNotEquals(token,rows.getString(1));
        }
        var session=anonymous();
        var joined=accept(token,PASSWORD,PASSWORD,session); assertEquals(302,joined.statusCode(),joined.body());
        assertTrue(joined.headers().firstValue("Location").orElseThrow().endsWith("/login"));
        var login=send("POST","/login",session,"username=new.member&password="+query(PASSWORD),"application/x-www-form-urlencoded");
        assertEquals(302,login.statusCode()); assertFalse(login.headers().firstValue("Location").orElseThrow().contains("error"));
        assertEquals(400,accept(token,PASSWORD,PASSWORD,anonymous()).statusCode());
        try(var connection=PostgresContainerHolder.getOwnerConnection(); var statement=connection.createStatement(); var rows=statement.executeQuery("select role,enabled from app_user where username='new.member'")) {
            assertTrue(rows.next()); assertEquals("EMPLOYEE",rows.getString(1)); assertTrue(rows.getBoolean(2));
        }
        assertFalse(output.getAll().contains(token)); assertFalse(output.getAll().contains(PASSWORD));
    }
    @Test void anonymousAndEmployeeCannotIssueOrRevokeAndCsrfIsRequired() throws Exception {
        var anon=anonymous();
        for(String path:new String[]{"/admin/invitations","/admin/invitations/1/revoke"}) {
            var anonymousResponse=send("POST",path,anon,"username=another&displayName=Another","application/x-www-form-urlencoded");
            assertEquals(302,anonymousResponse.statusCode());
            assertTrue(anonymousResponse.headers().firstValue("Location").orElseThrow().endsWith("/login"));
            assertEquals(403,send("POST",path,employee,"username=another&displayName=Another","application/x-www-form-urlencoded").statusCode());
        }
        var issued=invitations.issue(actor(),"csrf.member","CSRF member");
        assertEquals(403,accept(issued.token(),PASSWORD,PASSWORD,new Session(anon.cookie(),null)).statusCode());
        assertEquals(302,accept(issued.token(),PASSWORD,PASSWORD,anon).statusCode());
    }
    @Test void invalidWeakAndMismatchedPasswordsDoNotConsumeTokenOrEchoSecrets() throws Exception {
        var issued=invitations.issue(actor(),"validation.member","Validation member"); var anon=anonymous();
        for(String[] pair:new String[][]{{"short","short"},{PASSWORD,"Different-Confirmation"}}) {
            var failed=accept(issued.token(),pair[0],pair[1],anon); assertEquals(400,failed.statusCode(),failed.body());
            assertFalse(failed.body().contains(pair[0])); assertFalse(failed.body().contains(pair[1]));
        }
        assertEquals(302,accept(issued.token(),PASSWORD,PASSWORD,anon).statusCode());
        assertEquals(400,accept("bad-token",PASSWORD,PASSWORD,anonymous()).statusCode());
    }
    @Test void expiredAtExactBoundaryAndRevokedInvitationsAreRejected() throws Exception {
        var expired=invitations.issue(actor(),"expired.member","Expired");
        var revoked=invitations.issue(actor(),"revoked.member","Revoked");
        invitations.revoke(actor(),revoked.id());
        assertEquals(400,accept(revoked.token(),PASSWORD,PASSWORD,anonymous()).statusCode());
        clock.advanceBy(Duration.ofHours(48));
        assertEquals(400,accept(expired.token(),PASSWORD,PASSWORD,anonymous()).statusCode());
    }
    @Test void inactiveIssuerAndExistingAccountCannotBeBypassed() throws Exception {
        var issued=invitations.issue(actor(),"existing.member","Existing"); user("existing.member","Existing","EMPLOYEE");
        assertEquals(400,accept(issued.token(),PASSWORD,PASSWORD,anonymous()).statusCode());
        var disabled=invitations.issue(actor(),"disabled.issuer","Disabled issuer");
        try(var connection=PostgresContainerHolder.getOwnerConnection();var statement=connection.createStatement()) {
            statement.executeUpdate("update app_user set enabled=false where id="+adminId);
        }
        assertEquals(400,accept(disabled.token(),PASSWORD,PASSWORD,anonymous()).statusCode());
    }
    @Test void concurrentRedemptionCreatesOnlyOneAccount() throws Exception {
        var issued=invitations.issue(actor(),"race.member","Race member");
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> accept=()->{ready.countDown(); assertTrue(start.await(10,TimeUnit.SECONDS));
                try { redemption.accept(issued.token(),PASSWORD,PASSWORD); return true; }
                catch(DomainException exception) { assertEquals(Reason.INVITATION_INVALID,exception.getReason());return false; }};
            var first=executor.submit(accept);var second=executor.submit(accept);assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            assertNotEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }
        try(var connection=PostgresContainerHolder.getOwnerConnection();var statement=connection.createStatement();var rows=statement.executeQuery("select count(*) from app_user where username='race.member'")) {
            assertTrue(rows.next());assertEquals(1,rows.getInt(1));
        }
    }
    @Test void publicJoinHasCsrfLocalScriptAndNoReferrer() throws Exception {
        var page=api("GET","/join",null,"");assertEquals(200,page.statusCode(),page.body());
        assertTrue(page.body().contains("name=\"_csrf\""));assertTrue(page.body().contains("/js/ui.js"));
        assertEquals("no-referrer",page.headers().firstValue("Referrer-Policy").orElseThrow());
        assertTrue(page.headers().firstValue("Content-Security-Policy").orElseThrow().contains("form-action 'self'"));
    }
}
