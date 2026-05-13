package br.com.cezarcirqueira.mirror.app.adapter.in.web;

import br.com.cezarcirqueira.mirror.app.adapter.out.persistence.MirrorDefinitionRepository;
import br.com.cezarcirqueira.mirror.app.application.security.TokenEquals;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MirrorPeerAuthFilter extends OncePerRequestFilter {

    private final MirrorDefinitionRepository mirrorDefinitionRepository;

    public MirrorPeerAuthFilter(MirrorDefinitionRepository mirrorDefinitionRepository) {
        this.mirrorDefinitionRepository = mirrorDefinitionRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/v1/mirrors/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.endsWith("/health")) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<String> guidOpt = extractGuid(uri);
        if (guidOpt.isEmpty()) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid mirror path");
            return;
        }
        String guid = guidOpt.get();
        try {
            UUID.fromString(guid);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid GUID");
            return;
        }
        var mirror = mirrorDefinitionRepository.findById(guid).orElse(null);
        if (mirror == null) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "Unknown mirror");
            return;
        }
        String token = request.getHeader("X-Mirror-Token");
        if (!TokenEquals.timingSafeEqual(mirror.getSharedSecret(), token)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Optional<String> extractGuid(String uri) {
        String prefix = "/api/v1/mirrors/";
        if (!uri.startsWith(prefix)) {
            return Optional.empty();
        }
        String rest = uri.substring(prefix.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return Optional.of(rest);
        }
        return Optional.of(rest.substring(0, slash));
    }
}
