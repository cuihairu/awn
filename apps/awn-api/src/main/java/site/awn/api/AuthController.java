package site.awn.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import site.awn.api.auth.ApiTokenService;
import site.awn.identity.api.IdentityFacade;
import site.awn.identity.api.RegisterCommand;
import site.awn.identity.api.UserView;

/** Registration and login issue one-hour bearer access tokens. */
@RestController
public class AuthController {

    private final IdentityFacade identity;
    private final ApiTokenService tokens;

    public AuthController(IdentityFacade identity, ApiTokenService tokens) {
        this.identity = identity;
        this.tokens = tokens;
    }

    @PostMapping("/api/v1/auth/register")
    public ResponseEntity<AuthResponse> register(@RequestBody AuthRequest request) {
        UserView user = identity.register(new RegisterCommand(
                request.email(), request.password(), request.displayName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(tokens.issue(user.id()), user));
    }

    @PostMapping("/api/v1/auth/login")
    public AuthResponse login(@RequestBody AuthRequest request) {
        long userId = identity.verifyCredentials(request.email(), request.password());
        return new AuthResponse(tokens.issue(userId), identity.userOf(userId));
    }

    public record AuthRequest(String email, String password, String displayName) {
    }

    public record AuthResponse(String accessToken, UserView user) {
    }
}
