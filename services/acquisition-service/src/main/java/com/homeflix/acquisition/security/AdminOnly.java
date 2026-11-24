package com.homeflix.acquisition.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Every operation of the annotated controller is administrator only (ACQ-05). The check runs in
 * {@link AdminOnlyInterceptor}, before Spring reads the path variables or the body, so a caller who is not
 * an administrator gets 401 or 403 whatever the request holds (SEC-02).
 *
 * <p>The verified caller is then available as the request attribute {@link #CALLER}, for example with
 * {@code @RequestAttribute(AdminOnly.CALLER) Caller caller}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AdminOnly {

	/** Name of the request attribute that holds the verified administrator. */
	String CALLER = "com.homeflix.acquisition.security.AdminOnly.CALLER";

}
