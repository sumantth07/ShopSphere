package com.shopsphere.addresses;

import com.shopsphere.exception.ApiException;
import com.shopsphere.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {
    public record AddressRequest(@Size(max = 40) String label, @NotBlank @Size(max = 120) String fullName,
                                 @NotBlank @Pattern(regexp = "^[0-9+()\\-\\s]{7,30}$", message = "Enter a valid phone number.") String phone,
                                 @NotBlank @Size(max = 200) String line1, @Size(max = 200) String line2,
                                 @NotBlank @Size(max = 100) String city, @NotBlank @Size(max = 100) String state,
                                 @NotBlank @Size(max = 20) String postalCode, @NotBlank @Size(max = 60) String country, boolean isDefault) {}
    public record Address(long id, String label, String fullName, String phone, String line1, String line2, String city,
                          String state, String postalCode, String country, boolean isDefault) {}

    private final JdbcClient jdbc;
    public AddressController(JdbcClient jdbc) { this.jdbc = jdbc; }

    private List<Address> all(long uid) {
        return jdbc.sql("select id,label,full_name,phone,line1,line2,city,state,postal_code,country,is_default from addresses where user_id=:u order by is_default desc, id desc")
            .param("u", uid).query((rs, n) -> new Address(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getBoolean(11))).list();
    }

    @GetMapping public List<Address> list(@AuthenticationPrincipal Jwt jwt) { return all(CurrentUser.id(jwt)); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Address create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest r) {
        long uid = CurrentUser.id(jwt);
        boolean first = all(uid).isEmpty();
        boolean def = r.isDefault() || first;
        if (def) jdbc.sql("update addresses set is_default=false where user_id=:u").param("u", uid).update();
        long id = jdbc.sql("""
            insert into addresses (user_id,label,full_name,phone,line1,line2,city,state,postal_code,country,is_default)
            values (:u,:label,:fn,:ph,:l1,:l2,:city,:st,:pc,:co,:d) returning id""")
            .param("u", uid).param("label", r.label(), java.sql.Types.VARCHAR).param("fn", r.fullName().trim()).param("ph", r.phone().trim()).param("l1", r.line1().trim())
            .param("l2", r.line2(), java.sql.Types.VARCHAR).param("city", r.city().trim()).param("st", r.state().trim()).param("pc", r.postalCode().trim())
            .param("co", r.country().trim()).param("d", def).query(Long.class).single();
        return all(uid).stream().filter(a -> a.id() == id).findFirst().orElseThrow();
    }

    @PutMapping("/{id}") @Transactional
    public Address update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody AddressRequest r) {
        long uid = CurrentUser.id(jwt);
        if (r.isDefault()) jdbc.sql("update addresses set is_default=false where user_id=:u and id<>:id").param("u", uid).param("id", id).update();
        int n = jdbc.sql("""
            update addresses set label=:label, full_name=:fn, phone=:ph, line1=:l1, line2=:l2, city=:city, state=:st, postal_code=:pc, country=:co,
                   is_default = (is_default or :d) where id=:id and user_id=:u""")
            .param("label", r.label(), java.sql.Types.VARCHAR).param("fn", r.fullName().trim()).param("ph", r.phone().trim()).param("l1", r.line1().trim())
            .param("l2", r.line2(), java.sql.Types.VARCHAR).param("city", r.city().trim()).param("st", r.state().trim()).param("pc", r.postalCode().trim())
            .param("co", r.country().trim()).param("d", r.isDefault()).param("id", id).param("u", uid).update();
        if (n == 0) throw ApiException.notFound("Address not found.");
        return all(uid).stream().filter(a -> a.id() == id).findFirst().orElseThrow();
    }

    @DeleteMapping("/{id}") @Transactional
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        long uid = CurrentUser.id(jwt);
        if (jdbc.sql("delete from addresses where id=:id and user_id=:u").param("id", id).param("u", uid).update() == 0) throw ApiException.notFound("Address not found.");
        jdbc.sql("update addresses set is_default=true where id=(select id from addresses where user_id=:u order by id desc limit 1) and not exists(select 1 from addresses where user_id=:u and is_default)")
            .param("u", uid).update();
    }
}
