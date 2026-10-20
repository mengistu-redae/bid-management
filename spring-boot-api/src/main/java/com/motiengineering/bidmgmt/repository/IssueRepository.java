package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.Issue;
import com.motiengineering.bidmgmt.domain.enums.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IssueRepository extends JpaRepository<Issue, UUID> {

    // A derived-query method name here would misparse: "itemOrSystem" contains the literal
    // substring "Or", which Spring Data's query derivation reads as the boolean keyword rather
    // than part of the property name - hence the explicit JPQL.
    @Query("SELECT i FROM Issue i WHERE i.organization.id = :organizationId AND i.itemOrSystem = :itemOrSystem AND i.status NOT IN :excludedStatuses")
    List<Issue> findOpenByOrganizationAndItem(@Param("organizationId") UUID organizationId,
                                               @Param("itemOrSystem") String itemOrSystem,
                                               @Param("excludedStatuses") List<IssueStatus> excludedStatuses);
}
