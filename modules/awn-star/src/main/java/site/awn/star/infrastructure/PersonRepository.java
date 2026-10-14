package site.awn.star.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.star.domain.Person;

import java.util.List;

public interface PersonRepository extends JpaRepository<Person, Long> {

    List<Person> findByStatusOrderByIdDesc(String status);

    List<Person> findByStatusAndNationalityIgnoreCaseOrderByIdDesc(String status, String nationality);
}
