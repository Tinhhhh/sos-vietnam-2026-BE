package com.sosvietnam.repository;

import com.sosvietnam.model.entity.IncidentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface IncidentDocumentRepository extends JpaRepository<IncidentDocument, String> {

    /** Văn bản của các ca trong 1 trang lịch sử (chỉ cần biết có / đã ký đủ chưa) */
    @Query("select d from IncidentDocument d where d.incidentId in :ids")
    List<IncidentDocument> findByIncidentIds(@Param("ids") Collection<String> ids);
}
