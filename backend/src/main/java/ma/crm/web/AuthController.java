package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.dto.AuthDtos.*;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.UserDtos.LoginLogDto;
import ma.crm.dto.UserDtos.ProfileRequest;
import ma.crm.dto.UserDtos.UserDto;
import ma.crm.security.AppUserDetails;
import ma.crm.service.AuthService;
import ma.crm.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification et profil")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return authService.login(req, http.getRemoteAddr(), http.getHeader("User-Agent"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AppUserDetails user) {
        return authService.me(user.id());
    }

    @PutMapping("/me")
    public UserDto updateProfile(@AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody ProfileRequest req) {
        return userService.updateProfile(user.id(), req);
    }

    @PostMapping("/me/password")
    public AuthResponse changePassword(@AuthenticationPrincipal AppUserDetails user,
                                       @Valid @RequestBody ChangePasswordRequest req) {
        return authService.changePassword(user.id(), req);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AppUserDetails user) {
        authService.logout(user.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/logins")
    public PageResponse<LoginLogDto> myLogins(@AuthenticationPrincipal AppUserDetails user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return userService.loginHistory(user.email(), page, Math.min(size, 100));
    }
}
