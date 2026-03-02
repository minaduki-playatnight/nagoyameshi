package com.example.nagoyameshi.Security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
	    http
	        // ✅ Stripe Webhook は CSRF 例外にする
	        .csrf(csrf -> csrf.ignoringRequestMatchers("/stripe/webhook"))

	        .authorizeHttpRequests(requests -> requests
	            // 静的ファイル
	            .requestMatchers("/css/**", "/images/**", "/js/**", "/storage/**").permitAll()

	            // 公開ページ
	            .requestMatchers("/", "/signup/**", "/shops/**").permitAll()

	            // ✅ Webhook は外部（Stripe）から来るので permitAll
	            .requestMatchers("/stripe/webhook").permitAll()

	            // ✅ サブスク管理画面はログイン必須（permitAll側に入れない）
	            .requestMatchers("/subscription/**").authenticated()

	            // 管理者
	            .requestMatchers("/admin/**").hasRole("ADMIN")

	            .anyRequest().authenticated()
	        )
	        .formLogin(form -> form
	            .loginPage("/login")
	            .loginProcessingUrl("/login")
	            .defaultSuccessUrl("/?loggedIn")
	            .failureUrl("/login?error")
	            .permitAll()
	        )
	        .logout(logout -> logout
	            .logoutSuccessUrl("/?loggedOut")
	            .permitAll()
	        );

	    return http.build();
	}
	
	   @Bean
	    public PasswordEncoder passwordEncoder() {
	        return new BCryptPasswordEncoder();
	    }
	   
}
