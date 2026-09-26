package dev.kryora.live.access;

import dev.kryora.live.config.KryoraProperties;
import dev.kryora.live.user.User;
import dev.kryora.live.user.UserRepository;
import dev.kryora.live.user.UserStatus;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Every rule about who gets in: requesting, approving, declining and removing. */
@Service
public class AccessService {

    static final int MAX_MESSAGE_LENGTH = 500;

    private final AccessRequestRepository requests;
    private final UserRepository users;
    private final KryoraProperties properties;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public AccessService(AccessRequestRepository requests, UserRepository users, KryoraProperties properties,
                         FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.requests = requests;
        this.users = users;
        this.properties = properties;
        this.sessions = sessions;
    }

    @Transactional
    public void requestAccess(User user, String message) {
        if (user.getStatus() == UserStatus.MEMBER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have access");
        }
        if (user.getStatus() == UserStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Your request is already waiting for approval");
        }
        String cleaned = message == null ? null : message.strip();
        if (cleaned != null && cleaned.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Message is too long (max " + MAX_MESSAGE_LENGTH + " characters)");
        }

        requests.save(new AccessRequest(user, cleaned == null || cleaned.isEmpty() ? null : cleaned));
        user.changeStatus(UserStatus.PENDING);
        users.save(user);
    }

    @Transactional(readOnly = true)
    public List<AccessRequestView> pendingRequests(User admin) {
        requireAdmin(admin);
        return requests.findByStatusOrderByCreatedAtAsc(AccessRequestStatus.PENDING).stream()
                .map(AccessRequestView::from)
                .toList();
    }

    @Transactional
    public void approve(Long requestId, User admin) {
        decide(requestId, admin, AccessRequestStatus.APPROVED, UserStatus.MEMBER);
    }

    @Transactional
    public void decline(Long requestId, User admin) {
        decide(requestId, admin, AccessRequestStatus.DECLINED, UserStatus.DECLINED);
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(User admin) {
        requireAdmin(admin);
        return users.findByStatusOrderByNameAsc(UserStatus.MEMBER).stream()
                .map(user -> MemberView.from(user, properties.isAdmin(user.getEmail())))
                .toList();
    }

    /** Takes access away and signs the person out everywhere. */
    @Transactional
    public void removeMember(Long userId, User admin) {
        requireAdmin(admin);
        User member = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such user"));
        if (properties.isAdmin(member.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Admins can't be removed here; take them out of ADMIN_EMAILS instead");
        }
        member.changeStatus(UserStatus.NONE);
        users.save(member);

        if (member.getGoogleSubject() != null) {
            sessions.findByPrincipalName(member.getGoogleSubject()).keySet().forEach(sessions::deleteById);
        }
    }

    private void decide(Long requestId, User admin, AccessRequestStatus decision, UserStatus newUserStatus) {
        requireAdmin(admin);
        AccessRequest request = requests.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such request"));
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request was already decided");
        }
        request.decide(decision, admin);
        request.getUser().changeStatus(newUserStatus);
    }

    /** Second line of defense: the security config already blocks non-admins from /api/admin. */
    private void requireAdmin(User user) {
        if (!properties.isAdmin(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
