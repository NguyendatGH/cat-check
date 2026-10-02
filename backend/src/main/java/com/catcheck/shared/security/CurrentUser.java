package com.catcheck.shared.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation của {@link AuthenticationPrincipal}: bind tham số controller vào
 * {@link SecurityPrincipal} đang đăng nhập. Ví dụ:
 * {@code void foo(@CurrentUser SecurityPrincipal user)}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal
public @interface CurrentUser {
}
