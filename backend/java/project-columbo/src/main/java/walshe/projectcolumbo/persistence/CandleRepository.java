package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandleRepository extends JpaRepository<Candle, Long> {
}
