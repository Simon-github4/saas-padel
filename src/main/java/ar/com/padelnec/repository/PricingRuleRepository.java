package ar.com.padelnec.repository;

import ar.com.padelnec.domain.PricingRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    List<PricingRule> findAllByDayOfWeek(int dayOfWeek);

    List<PricingRule> findAllByOrderByDayOfWeekAscStartTimeAsc();
}
