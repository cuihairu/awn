package site.awn.star.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.star.api.BindCastCommand;
import site.awn.star.api.CreatePersonCommand;
import site.awn.star.api.CreateWorkCommand;
import site.awn.star.api.PersonDetail;
import site.awn.star.api.PersonView;
import site.awn.star.api.PersonWorkEntry;
import site.awn.star.api.StarFacade;
import site.awn.star.api.WorkDetail;
import site.awn.star.api.WorkView;
import site.awn.star.domain.Person;
import site.awn.star.domain.PersonNotFoundException;
import site.awn.star.domain.PersonWork;
import site.awn.star.domain.StarCatalogTypes;
import site.awn.star.domain.StarConflictException;
import site.awn.star.domain.Work;
import site.awn.star.domain.WorkNotFoundException;
import site.awn.star.infrastructure.PersonRepository;
import site.awn.star.infrastructure.PersonWorkRepository;
import site.awn.star.infrastructure.WorkRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 图鉴用例实现。
 * 检索口径（用户 2026-10-14 拍板）：姓名 / 别名 / 国籍 / 类型的基础匹配，
 * 大小写不敏感包含；不做全文与拼音模糊。
 * 公开面守卫：人物 / 作品只透出 PUBLISHED；人物的作品列表只含已发布作品，
 * 作品的人员表只含已发布人物——草稿内容不跨实体泄露。
 */
@Service
public class StarFacadeImpl implements StarFacade {

    private final PersonRepository persons;
    private final WorkRepository works;
    private final PersonWorkRepository bindings;

    public StarFacadeImpl(PersonRepository persons, WorkRepository works, PersonWorkRepository bindings) {
        this.persons = persons;
        this.works = works;
        this.bindings = bindings;
    }

    @Override
    @Transactional
    public PersonView createPerson(CreatePersonCommand command) {
        String name = trimToNull(command.name());
        if (name == null) {
            throw new IllegalArgumentException("person name is required");
        }
        String nationality = trimToNull(command.nationality());
        if (nationality == null) {
            throw new IllegalArgumentException("person nationality is required");
        }
        List<String> professionTypes = distinctValidated(command.professionTypes(),
                StarCatalogTypes.PROFESSIONS, "profession");
        String photoUrl = trimToNull(command.photoUrl());
        String photoSource = trimToNull(command.photoSource());
        if (photoUrl != null && photoSource == null) {
            throw new IllegalArgumentException("photo source is required for a licensed photo");
        }

        Person person = new Person(name, trimToNull(command.originalName()), cleanList(command.aliases()),
                nationality, professionTypes, trimToNull(command.bio()), photoUrl, photoSource);
        return toView(persons.saveAndFlush(person));
    }

    @Override
    @Transactional
    public PersonView publishPerson(long personId) {
        Person person = persons.findById(personId).orElseThrow(() -> new PersonNotFoundException(personId));
        person.publish();
        return toView(person);
    }

    @Override
    @Transactional
    public WorkView createWork(CreateWorkCommand command) {
        String title = trimToNull(command.title());
        if (title == null) {
            throw new IllegalArgumentException("work title is required");
        }
        String type = trimToNull(command.type());
        if (type == null || !StarCatalogTypes.WORK_TYPES.contains(type)) {
            throw new IllegalArgumentException("unknown work type: " + command.type());
        }
        Integer year = command.year();
        if (year != null && (year < 1900 || year > 2100)) {
            throw new IllegalArgumentException("work year out of range: " + year);
        }
        Work work = new Work(title, type, year, trimToNull(command.description()));
        return toView(works.saveAndFlush(work));
    }

    @Override
    @Transactional
    public WorkView publishWork(long workId) {
        Work work = works.findById(workId).orElseThrow(() -> new WorkNotFoundException(workId));
        work.publish();
        return toView(work);
    }

    @Override
    @Transactional
    public PersonWorkEntry bindCast(long workId, BindCastCommand command) {
        Work work = works.findById(workId).orElseThrow(() -> new WorkNotFoundException(workId));
        Person person = persons.findById(command.personId())
                .orElseThrow(() -> new PersonNotFoundException(command.personId()));
        String roleType = trimToNull(command.roleType());
        if (roleType == null || !StarCatalogTypes.ROLE_TYPES.contains(roleType)) {
            throw new IllegalArgumentException("unknown role type: " + command.roleType());
        }
        String roleName = command.roleName() == null ? "" : command.roleName().trim();
        if (bindings.existsByPersonIdAndWorkIdAndRoleTypeAndRoleName(
                person.getId(), work.getId(), roleType, roleName)) {
            throw new StarConflictException("duplicate cast binding: person " + person.getId()
                    + " work " + work.getId() + " role " + roleType + " '" + roleName + "'");
        }
        bindings.saveAndFlush(new PersonWork(person.getId(), work.getId(), roleType,
                roleName, command.billingOrder()));
        return new PersonWorkEntry(person.getId(), work.getId(), person.getName(),
                work.getTitle(), work.getType(), work.getYear(), roleType, roleName, command.billingOrder());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonView> publicPersons(String profession, String nationality, String q) {
        String professionCode = trimToNull(profession);
        String nationalityCode = trimToNull(nationality);
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        return persons.findByStatusOrderByIdDesc(Person.STATUS_PUBLISHED).stream()
                .filter(p -> professionCode == null || p.getProfessionTypes().contains(professionCode))
                .filter(p -> nationalityCode == null
                        || p.getNationality().equalsIgnoreCase(nationalityCode))
                .filter(p -> needle == null || matchesNeedle(p, needle))
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PersonDetail publicPerson(long personId) {
        Person person = publishedPerson(personId);
        List<PersonWorkEntry> entries = new ArrayList<>();
        for (PersonWork binding : bindings.findByPersonId(personId)) {
            Work work = works.findById(binding.getWorkId())
                    .orElseThrow(() -> new WorkNotFoundException(binding.getWorkId()));
            if (!Work.STATUS_PUBLISHED.equals(work.getStatus())) {
                continue;
            }
            entries.add(new PersonWorkEntry(person.getId(), work.getId(), person.getName(),
                    work.getTitle(), work.getType(), work.getYear(),
                    binding.getRoleType(), binding.getRoleName(), binding.getBillingOrder()));
        }
        entries.sort(castOrder());
        return new PersonDetail(toView(person), entries);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkView> publicWorks(String type, String q) {
        String typeCode = trimToNull(type);
        List<Work> published = typeCode == null
                ? works.findByStatusOrderByIdDesc(Work.STATUS_PUBLISHED)
                : works.findByStatusAndTypeOrderByIdDesc(Work.STATUS_PUBLISHED, typeCode);
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        return published.stream()
                .filter(w -> needle == null || w.getTitle().toLowerCase(Locale.ROOT).contains(needle))
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkDetail publicWork(long workId) {
        Work work = works.findById(workId)
                .filter(w -> Work.STATUS_PUBLISHED.equals(w.getStatus()))
                .orElseThrow(() -> new WorkNotFoundException(workId));
        List<PersonWorkEntry> cast = new ArrayList<>();
        for (PersonWork binding : bindings.findByWorkId(workId)) {
            Person person = persons.findById(binding.getPersonId())
                    .orElseThrow(() -> new PersonNotFoundException(binding.getPersonId()));
            if (!Person.STATUS_PUBLISHED.equals(person.getStatus())) {
                continue;
            }
            cast.add(new PersonWorkEntry(person.getId(), work.getId(), person.getName(),
                    work.getTitle(), work.getType(), work.getYear(),
                    binding.getRoleType(), binding.getRoleName(), binding.getBillingOrder()));
        }
        cast.sort(castOrder());
        return new WorkDetail(toView(work), cast);
    }

    private Person publishedPerson(long personId) {
        return persons.findById(personId)
                .filter(p -> Person.STATUS_PUBLISHED.equals(p.getStatus()))
                .orElseThrow(() -> new PersonNotFoundException(personId));
    }

    /** 排序：billingOrder 升序（null 在后），再按身份与对端 id，保证稳定输出 */
    private Comparator<PersonWorkEntry> castOrder() {
        return Comparator
                .comparing(PersonWorkEntry::billingOrder,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PersonWorkEntry::roleType)
                .thenComparing(PersonWorkEntry::workId)
                .thenComparing(PersonWorkEntry::personId);
    }

    /** q 命中：姓名 / 原名 / 任一别名的大小写不敏感包含 */
    private boolean matchesNeedle(Person person, String needle) {
        if (person.getName() != null && person.getName().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (person.getOriginalName() != null
                && person.getOriginalName().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return person.getAliases() != null && person.getAliases().stream()
                .anyMatch(a -> a != null && a.toLowerCase(Locale.ROOT).contains(needle));
    }

    private List<String> distinctValidated(List<String> raw, Set<String> whitelist, String label) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String item : raw) {
            String value = item == null ? null : item.trim();
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException(label + " must not be blank");
            }
            if (!whitelist.contains(value)) {
                throw new IllegalArgumentException("unknown " + label + ": " + item);
            }
            cleaned.add(value);
        }
        return new ArrayList<>(cleaned);
    }

    private List<String> cleanList(List<String> raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        return raw.stream()
                .map(a -> a == null ? null : a.trim())
                .filter(a -> a != null && !a.isEmpty())
                .distinct()
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 公开视图：photoUrl 只有在带授权来源说明时才透出（素材红线） */
    private PersonView toView(Person person) {
        return new PersonView(person.getId(), person.getName(), person.getOriginalName(),
                person.getAliases(), person.getNationality(), person.getProfessionTypes(),
                person.getBio(), person.hasPublishablePhoto() ? person.getPhotoUrl() : null,
                person.getStatus());
    }

    private WorkView toView(Work work) {
        return new WorkView(work.getId(), work.getTitle(), work.getType(),
                work.getYear(), work.getDescription(), work.getStatus());
    }
}
