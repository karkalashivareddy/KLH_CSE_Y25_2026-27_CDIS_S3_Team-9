package com.cloudresilience.modules.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final JdbcTemplate jdbc;
    public CatalogController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record CategoryInput(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=140) @Pattern(regexp="[a-z0-9]+(-[a-z0-9]+)*") String slug, @Size(max=1000) String description, Boolean active) {}
    public record ProductInput(@NotBlank @Size(max=64) String sku, @NotBlank @Size(max=180) String name, @NotBlank @Size(max=200) @Pattern(regexp="[a-z0-9]+(-[a-z0-9]+)*") String slug, @Size(max=500) String shortDescription, @Size(max=10000) String description, @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal price, @NotNull UUID categoryId, @Size(max=2048) String imageUrl) {}
    public record StatusInput(@NotBlank String status) {}
    public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    @GetMapping("/categories")
    List<Map<String,Object>> categories() { return jdbc.queryForList("SELECT id,name,slug,description,active,created_at,updated_at FROM categories WHERE active ORDER BY name,id"); }
    @GetMapping("/admin/categories")
    List<Map<String,Object>> adminCategories() { return jdbc.queryForList("SELECT id,name,slug,description,active,created_at,updated_at FROM categories ORDER BY name,id"); }
    @GetMapping("/categories/{id}")
    Map<String,Object> category(@PathVariable UUID id) { return jdbc.query("SELECT id,name,slug,description,active,created_at,updated_at FROM categories WHERE id=? AND active", (rs, n) -> row(rs), id).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    @PostMapping("/admin/categories") @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> createCategory(@Valid @RequestBody CategoryInput input) {
        var id=UUID.randomUUID(); jdbc.update("INSERT INTO categories(id,name,slug,description,active) VALUES(?,?,?,?,?)",id,input.name().trim(),input.slug(),input.description(),input.active()==null||input.active()); return jdbc.queryForMap("SELECT id,name,slug,description,active,created_at,updated_at FROM categories WHERE id=?",id);
    }
    @PutMapping("/admin/categories/{id}")
    Map<String,Object> updateCategory(@PathVariable UUID id,@Valid @RequestBody CategoryInput input) {
        if (jdbc.update("UPDATE categories SET name=?,slug=?,description=?,active=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",input.name().trim(),input.slug(),input.description(),input.active()==null||input.active(),id)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return jdbc.queryForMap("SELECT id,name,slug,description,active,created_at,updated_at FROM categories WHERE id=?",id);
    }

    @GetMapping("/products")
    PageResult<Map<String,Object>> products(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="asc") String direction,@RequestParam(required=false) UUID categoryId,@RequestParam(required=false) @Size(max=100) String q) {
        if(page<0||size<1||size>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid pagination");
        var columns=Map.of("name","p.name","sku","p.sku","price","p.price","createdAt","p.created_at"); String column=columns.get(sort);
        if(column==null||!(direction.equalsIgnoreCase("asc")||direction.equalsIgnoreCase("desc"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported sort");
        StringBuilder filter=new StringBuilder(" AND c.active"); var args=new java.util.ArrayList<Object>();
        if(categoryId!=null){filter.append(" AND p.category_id=?");args.add(categoryId);}
        if(q!=null&&!q.isBlank()) { String pattern="%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"; filter.append(" AND (lower(p.name) LIKE lower(?) ESCAPE '\\' OR lower(p.sku) LIKE lower(?) ESCAPE '\\')");args.add(pattern);args.add(pattern); }
        String where=" WHERE p.status='ACTIVE'"+filter;
        long count=jdbc.queryForObject("SELECT count(*) FROM products p JOIN categories c ON c.id=p.category_id"+where,args.toArray(),Long.class);
        String sql="SELECT p.id,p.sku,p.name,p.slug,p.short_description,p.description,p.price,p.currency,p.category_id,c.name category_name,p.image_url,p.status,p.created_at,p.updated_at,COALESCE(i.quantity_on_hand-i.quantity_reserved,0) available_quantity FROM products p JOIN categories c ON c.id=p.category_id LEFT JOIN inventory i ON i.product_id=p.id"+where+" ORDER BY "+column+" "+direction.toUpperCase()+",p.id ASC LIMIT ? OFFSET ?";
        args.add(size); args.add((long)page*size);
        List<Map<String,Object>> content=jdbc.queryForList(sql,args.toArray()); return new PageResult<>(content,page,size,count,(int)((count+size-1)/size));
    }
    @GetMapping("/admin/products")
    List<Map<String,Object>> adminProducts() {
        return jdbc.queryForList("SELECT p.id,p.sku,p.name,p.slug,p.short_description,p.description,p.price,p.currency,p.category_id,c.name category_name,p.image_url,p.status,p.created_at,COALESCE(i.quantity_on_hand-i.quantity_reserved,0) available_quantity FROM products p JOIN categories c ON c.id=p.category_id LEFT JOIN inventory i ON i.product_id=p.id ORDER BY p.created_at DESC,p.id LIMIT 200");
    }
    @GetMapping("/products/{id}") Map<String,Object> product(@PathVariable UUID id) { return publicProduct(id); }
    @GetMapping("/products/{id}/availability") Map<String,Object> availability(@PathVariable UUID id) {
        var row=jdbc.queryForList("SELECT p.id,COALESCE(i.quantity_on_hand-i.quantity_reserved,0) available_quantity FROM products p JOIN categories c ON c.id=p.category_id LEFT JOIN inventory i ON i.product_id=p.id WHERE p.id=? AND p.status='ACTIVE' AND c.active",id);
        return row.stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @PostMapping("/admin/products") @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> createProduct(@Valid @RequestBody ProductInput in) {
        validateImage(in.imageUrl()); requireActiveCategory(in.categoryId()); UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO products(id,sku,name,slug,short_description,description,price,category_id,image_url) VALUES(?,?,?,?,?,?,?,?,?)",id,in.sku().trim().toUpperCase(),in.name().trim(),in.slug(),in.shortDescription(),in.description(),in.price(),in.categoryId(),in.imageUrl());
        return adminProduct(id);
    }
    @PutMapping("/admin/products/{id}") Map<String,Object> updateProduct(@PathVariable UUID id,@Valid @RequestBody ProductInput in) {
        validateImage(in.imageUrl()); requireActiveCategory(in.categoryId()); if(jdbc.update("UPDATE products SET sku=?,name=?,slug=?,short_description=?,description=?,price=?,category_id=?,image_url=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",in.sku().trim().toUpperCase(),in.name().trim(),in.slug(),in.shortDescription(),in.description(),in.price(),in.categoryId(),in.imageUrl(),id)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return adminProduct(id);
    }
    @PatchMapping("/admin/products/{id}/status") Map<String,Object> status(@PathVariable UUID id,@Valid @RequestBody StatusInput in) {
        if(!List.of("DRAFT","ACTIVE","ARCHIVED").contains(in.status())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid status");
        String current=jdbc.query("SELECT status FROM products WHERE id=?",rs->rs.next()?rs.getString(1):null,id); if(current==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        boolean allowed=current.equals(in.status()) || current.equals("DRAFT")&&List.of("ACTIVE","ARCHIVED").contains(in.status()) || current.equals("ACTIVE")&&in.status().equals("ARCHIVED") || current.equals("ARCHIVED")&&in.status().equals("ACTIVE");
        if(!allowed)throw new ResponseStatusException(HttpStatus.CONFLICT,"Unsupported product status transition");
        if(in.status().equals("ACTIVE")&&jdbc.queryForObject("SELECT active FROM categories WHERE id=(SELECT category_id FROM products WHERE id=?)",Boolean.class,id)!=Boolean.TRUE)throw new ResponseStatusException(HttpStatus.CONFLICT,"Active category required to publish product");
        jdbc.update("UPDATE products SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",in.status(),id); return adminProduct(id);
    }
    private Map<String,Object> publicProduct(UUID id) {
        var rows=jdbc.queryForList("SELECT p.id,p.sku,p.name,p.slug,p.short_description,p.description,p.price,p.currency,p.category_id,c.name category_name,p.image_url,p.status,p.created_at,p.updated_at,COALESCE(i.quantity_on_hand-i.quantity_reserved,0) available_quantity FROM products p JOIN categories c ON c.id=p.category_id LEFT JOIN inventory i ON i.product_id=p.id WHERE p.id=? AND p.status='ACTIVE' AND c.active",id);
        return rows.stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private Map<String,Object> adminProduct(UUID id) { return jdbc.queryForMap("SELECT * FROM products WHERE id=?",id); }
    private void validateImage(String url) { if(url!=null) { try { URI uri=URI.create(url); if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null) throw new IllegalArgumentException(); } catch(IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Image URL must be an HTTPS URL",e); } } }
    private void requireActiveCategory(UUID id) { if(jdbc.query("SELECT id FROM categories WHERE id=? AND active",(rs,n)->rs.getObject(1),id).isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"An active category is required"); }
    private Map<String,Object> row(java.sql.ResultSet rs) throws java.sql.SQLException { var m=new java.util.LinkedHashMap<String,Object>(); for(int i=1;i<=rs.getMetaData().getColumnCount();i++)m.put(rs.getMetaData().getColumnLabel(i),rs.getObject(i));return m; }
}
