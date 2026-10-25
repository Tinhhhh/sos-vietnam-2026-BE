package com.sosvietnam.security;

import com.sosvietnam.repository.UnitMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Dựng OfficerPrincipal từ claim "mid" của access token (REST và STOMP CONNECT dùng chung). */
@Component
@RequiredArgsConstructor
public class OfficerPrincipalLoader {

    private final UnitMemberRepository unitMemberRepository;

    /** Cán bộ còn hoạt động, chưa bị khoá, thuộc tài khoản đơn vị còn hoạt động; khác thì rỗng. */
    @Transactional(readOnly = true)
    public Optional<OfficerPrincipal> load(UUID memberId) {
        if (memberId == null) return Optional.empty();
        return unitMemberRepository.findById(memberId)
                .filter(m -> m.isActive() && !m.isLocked())
                .filter(m -> m.getAccount().isActive() && !m.getAccount().isLocked())
                .map(OfficerPrincipal::of);
    }
}
