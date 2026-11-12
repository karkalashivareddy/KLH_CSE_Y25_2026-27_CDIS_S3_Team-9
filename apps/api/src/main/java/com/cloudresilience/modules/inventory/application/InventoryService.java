package com.cloudresilience.modules.inventory.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {
    private final JdbcTemplate jdbc;
    public InventoryService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional
    public Map<String,Object> initialize(UUID product, long quantity, String reason, UUID actor) {
        if(quantity<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity cannot be negative");
        exists(product);
        try { jdbc.update("INSERT INTO inventory(id,product_id,quantity_on_hand) VALUES(?,?,?)",UUID.randomUUID(),product,quantity); }
        catch(org.springframework.dao.DuplicateKeyException e) { throw conflict("Inventory already initialized"); }
        movement(product,"INITIAL_STOCK",quantity,0,0,quantity,0,0,reason,actor,null);
        return inventory(product);
    }

    @Transactional
    public Map<String,Object> adjust(UUID product,long delta,String reason,UUID actor) {
        if(delta==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Adjustment must be non-zero");
        var before=lockInventory(product); long on=(long)before.get("quantity_on_hand"), reserved=(long)before.get("quantity_reserved"), next;
        try { next=Math.addExact(on,delta); } catch(ArithmeticException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Adjustment is too large"); }
        if(next<reserved) throw conflict("Adjustment would reduce stock below reserved quantity");
        jdbc.update("UPDATE inventory SET quantity_on_hand=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE product_id=?",next,product);
        String type=delta>0?"STOCK_IN":"STOCK_OUT";
        movement(product,type,delta,0,on,next,reserved,reserved,reason,actor,null); return inventory(product);
    }

    @Transactional
    public Map<String,Object> reserve(UUID product,long quantity,String reason,UUID actor) {
        if(quantity<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity must be positive");
        var before=lockInventory(product); long on=(long)before.get("quantity_on_hand"), reserved=(long)before.get("quantity_reserved");
        if(on-reserved<quantity) throw conflict("Insufficient available inventory");
        long after; try { after=Math.addExact(reserved,quantity); } catch(ArithmeticException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity is too large"); } UUID id=UUID.randomUUID();
        jdbc.update("UPDATE inventory SET quantity_reserved=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE product_id=?",after,product);
        jdbc.update("INSERT INTO inventory_reservations(id,product_id,quantity,status,created_by) VALUES(?,?,?,'RESERVED',?)",id,product,quantity,actor);
        movement(product,"RESERVATION",0,quantity,on,on,reserved,after,reason,actor,id); return Map.of("reservationId",id,"inventory",inventory(product));
    }

    @Transactional
    public Map<String,Object> release(UUID product,UUID reservation,String reason,UUID actor) {
        var before=lockInventory(product);
        var rows=jdbc.queryForList("SELECT id,quantity,status FROM inventory_reservations WHERE id=? AND product_id=? FOR UPDATE",reservation,product);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Reservation not found");
        var r=rows.getFirst(); if(!"RESERVED".equals(r.get("status"))) throw conflict("Reservation is already released");
        long quantity=((Number)r.get("quantity")).longValue(), on=((Number)before.get("quantity_on_hand")).longValue(), reserved=((Number)before.get("quantity_reserved")).longValue();
        jdbc.update("UPDATE inventory SET quantity_reserved=quantity_reserved-?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE product_id=?",quantity,product);
        jdbc.update("UPDATE inventory_reservations SET status='RELEASED',released_at=CURRENT_TIMESTAMP WHERE id=?",reservation);
        movement(product,"RESERVATION_RELEASE",0,-quantity,on,on,reserved,reserved-quantity,reason,actor,reservation); return inventory(product);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> inventory(UUID product) {
        var rows=jdbc.queryForList("SELECT i.product_id,i.quantity_on_hand,i.quantity_reserved,i.quantity_on_hand-i.quantity_reserved available_quantity,i.version,i.updated_at FROM inventory i WHERE i.product_id=?",product);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Inventory not initialized"); return rows.getFirst();
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> movements(UUID product) { return jdbc.queryForList("SELECT id,movement_type,quantity_delta,reserved_delta,previous_quantity,new_quantity,previous_reserved,new_reserved,reason,reference_id,performed_by,created_at FROM inventory_movements WHERE product_id=? ORDER BY created_at DESC,id DESC LIMIT 200",product); }

    private Map<String,Object> lockInventory(UUID product) {
        var rows=jdbc.queryForList("SELECT product_id,quantity_on_hand,quantity_reserved FROM inventory WHERE product_id=? FOR UPDATE",product);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Inventory not initialized"); return rows.getFirst();
    }
    private void exists(UUID product) { if(jdbc.queryForObject("SELECT count(*) FROM products WHERE id=?",Integer.class,product)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"); }
    private void movement(UUID product,String type,long delta,long reservedDelta,long previous,long next,long previousReserved,long nextReserved,String reason,UUID actor,UUID reference) {
        jdbc.update("INSERT INTO inventory_movements(id,product_id,movement_type,quantity_delta,reserved_delta,previous_quantity,new_quantity,previous_reserved,new_reserved,reason,reference_id,performed_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),product,type,delta,reservedDelta,previous,next,previousReserved,nextReserved,reason,reference,actor);
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
