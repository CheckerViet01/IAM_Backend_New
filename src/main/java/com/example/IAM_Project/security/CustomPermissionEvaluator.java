package com.example.IAM_Project.security;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false;
        }

        String target = targetDomainObject.toString().toUpperCase();
        String perm = permission.toString().toUpperCase();

        // Check format: target + ":" + perm (e.g. USER:CREATE) or directly perm
        String requiredPermissionWithColon = target + ":" + perm;
        String requiredPermissionWithUnderscore = target + "_" + perm;
        String requiredPermissionDirect = perm;

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String auth = authority.getAuthority().toUpperCase();

            // Super Admin or SYSTEM_ADMIN has full permissions
            if ("ROLE_SYSTEM_ADMIN".equals(auth) || "SYSTEM_ADMIN".equals(auth) || "ROLE_ADMIN".equals(auth)) {
                return true;
            }

            // USER_MANAGER role has all USER permissions
            if (("ROLE_USER_MANAGER".equals(auth) || "USER_MANAGER".equals(auth)) && "USER".equals(target)) {
                return true;
            }

            if (auth.equals(requiredPermissionWithColon) 
                    || auth.equals(requiredPermissionWithUnderscore)
                    || auth.equals(requiredPermissionDirect)
                    || auth.equals("ROLE_" + requiredPermissionWithColon)
                    || auth.equals("ROLE_" + requiredPermissionWithUnderscore)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        return hasPermission(authentication, (Object) targetType, permission);
    }
}
