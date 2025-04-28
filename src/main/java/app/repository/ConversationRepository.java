package app.repository;

import app.model.Conversation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    /** Lấy conversation + memberships trong 1 truy vấn */
    @Query("""
           select c
           from  Conversation c
           left  join fetch c.memberships
           where c.id = :id
           """)
    Optional<Conversation> findByIdWithMembers(@Param("id") long id);
}
