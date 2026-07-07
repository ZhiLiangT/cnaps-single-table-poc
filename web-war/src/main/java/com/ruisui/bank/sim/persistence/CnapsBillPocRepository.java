package com.ruisui.bank.sim.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface CnapsBillPocRepository extends JpaRepository<CnapsBillPoc, String> {
    Optional<CnapsBillPoc> findByBillId(String billId);

    @Query("""
        select coalesce(max(v.serialNo), '0001999')
        from CnapsBillPoc v
        where v.workDate = :workDate and v.branchNo = :branchNo
        """)
    String findMaxSerialNo(@Param("workDate") LocalDate workDate, @Param("branchNo") String branchNo);

    Page<CnapsBillPoc> findByWorkDateAndBranchNoAndStatus(
        LocalDate workDate,
        String branchNo,
        String status,
        Pageable pageable
    );

    Page<CnapsBillPoc> findByWorkDateAndBranchNo(
        LocalDate workDate,
        String branchNo,
        Pageable pageable
    );
}
