/**
 * Model Validator: Populate tmp_invoice_open after C_Invoice Save
 * ================================================================
 * 
 * Purpose:
 * This model validator triggers after a C_Invoice record is saved (created or updated).
 * It calculates the open amount for the invoice using the invoiceopen() function
 * and populates or updates the tmp_invoice_open table.
 * 
 * Trigger Events:
 * - TYPE_AFTER_NEW (4): After a new invoice is created
 * - TYPE_AFTER_CHANGE (2): After an existing invoice is updated
 * 
 * Table: tmp_invoice_open
 * Fields:
 * - c_invoice_id: Invoice ID
 * - c_bpartner_id: Business Partner ID
 * - dateinvoiced: Invoice date
 * - openamt: Open amount (calculated by invoiceopen function)
 * 
 * Database Function Used:
 * - invoiceopen(c_invoice_id, NULL::numeric): Returns the open amount for the invoice
 * 
 * Context Variables (iDempiere Script Model Validator):
 * - A_PO: The persistent object (MInvoice)
 * - A_Type: Event type constant (TYPE_AFTER_NEW = 4, TYPE_AFTER_CHANGE = 2)
 * - Transaction name is retrieved from the persistent object: inv.get_TrxName()
 * 
 * Version: 20251106
 */

import org.compiere.model.MInvoice
import org.compiere.model.ModelValidator
import org.compiere.util.DB
import org.compiere.util.CLogger
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.logging.Level
import java.math.BigDecimal

// Logger
CLogger log = CLogger.getCLogger("ModelValidator.C_Invoice.PopulateTmpInvoiceOpen")

// Validate context - model validator should provide these variables
if (A_PO == null) {
    log.warning("Script not running in model validator context - missing 'A_PO' variable")
    return ""
}

// Only process after save events
// TYPE_AFTER_NEW = 4, TYPE_AFTER_CHANGE = 2
if (A_Type != ModelValidator.TYPE_AFTER_NEW && A_Type != ModelValidator.TYPE_AFTER_CHANGE) {
    // Not a relevant event, skip processing
    return ""
}

try {
    // Get the invoice object
    if (!(A_PO instanceof MInvoice)) {
        log.warning("Invalid invoice object type")
        return ""
    }
    
    MInvoice inv = (MInvoice) A_PO
    Integer invoiceId = inv.get_ID()
    
    if (invoiceId == null || invoiceId <= 0) {
        log.warning("Invalid C_Invoice_ID: ${invoiceId}")
        return ""
    }
    
    if (!inv.isSOTrx()) {
        // Only process sales invoices (customer invoices)
        log.fine("Skipping purchase invoice ID ${invoiceId}")
        return ""
    }
    
    Integer bpartnerId = inv.getC_BPartner_ID()
    java.sql.Timestamp dateInvoiced = inv.getDateInvoiced()
    
    if (bpartnerId == null || bpartnerId <= 0) {
        log.warning("Invalid C_BPartner_ID for invoice ${invoiceId}")
        return ""
    }
    
    // Get transaction name from the persistent object
    // This is more reliable than using A_TrxName which may not always be available
    String trxName = inv.get_TrxName()
    
    // Call invoiceopen function to get the current open amount
    BigDecimal openAmt = null
    String selectSql = "SELECT invoiceopen(?, NULL::numeric) as openamt"
    PreparedStatement pstmt = null
    ResultSet rs = null
    
    try {
        pstmt = DB.prepareStatement(selectSql, trxName)
        pstmt.setInt(1, invoiceId)
        rs = pstmt.executeQuery()
        
        if (rs.next()) {
            openAmt = rs.getBigDecimal("openamt")
        }
    } catch (SQLException e) {
        log.log(Level.SEVERE, "Error calling invoiceopen function for invoice ${invoiceId}", e)
        return ""
    } finally {
        DB.close(rs, pstmt)
    }
    
    if (openAmt == null) {
        log.warning("invoiceopen function returned NULL for invoice ${invoiceId}")
        openAmt = BigDecimal.ZERO
    }
    
    // Upsert into tmp_invoice_open table
    // Using PostgreSQL's ON CONFLICT clause for atomic upsert
    String upsertSql = """
        INSERT INTO tmp_invoice_open (c_invoice_id, c_bpartner_id, dateinvoiced, saldo)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (c_invoice_id) 
        DO UPDATE SET 
            c_bpartner_id = EXCLUDED.c_bpartner_id,
            dateinvoiced = EXCLUDED.dateinvoiced,
            saldo = EXCLUDED.saldo
    """
    
    PreparedStatement upsertStmt = null
    
    try {
        upsertStmt = DB.prepareStatement(upsertSql, trxName)
        upsertStmt.setInt(1, invoiceId)
        upsertStmt.setInt(2, bpartnerId)
        upsertStmt.setTimestamp(3, dateInvoiced)
        upsertStmt.setBigDecimal(4, openAmt)
        
        int result = upsertStmt.executeUpdate()
        
        log.info("Successfully updated tmp_invoice_open for invoice ${invoiceId} (openamt: ${openAmt}, rows affected: ${result})")
        
    } catch (SQLException e) {
        log.log(Level.SEVERE, "Error upserting to tmp_invoice_open for invoice ${invoiceId}", e)
        return ""
    } finally {
        DB.close(upsertStmt)
    }
    
    // Success
    return ""
    
} catch (Exception e) {
    log.log(Level.SEVERE, "Unexpected error in C_Invoice model validator", e)
    return ""
}
