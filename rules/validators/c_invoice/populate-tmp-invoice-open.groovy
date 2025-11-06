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
 * - TYPE_AFTER_NEW: After a new invoice is created
 * - TYPE_AFTER_CHANGE: After an existing invoice is updated
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
 * Version: 20251106
 */

import org.compiere.model.MInvoice
import org.compiere.util.DB
import org.compiere.util.CLogger
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.logging.Level
import java.math.BigDecimal

// Logger
CLogger log = CLogger.getCLogger("ModelValidator.C_Invoice.PopulateTmpInvoiceOpen")

// Validate that this is a model validator context
if (A_Tab == null || A_TrxName == null) {
    log.warning("Script not running in model validator context")
    return "Error: Invalid context"
}

// Only process after save events
String eventType = TYPE
if (eventType != "TYPE_AFTER_NEW" && eventType != "TYPE_AFTER_CHANGE") {
    // Not a relevant event, skip processing
    return ""
}

try {
    // Get the invoice ID from the tab
    Integer invoiceId = A_Tab.getValue("C_Invoice_ID") as Integer
    
    if (invoiceId == null || invoiceId <= 0) {
        log.warning("Invalid C_Invoice_ID: ${invoiceId}")
        return "Error: Invalid Invoice ID"
    }
    
    // Load the invoice to get additional details
    MInvoice invoice = new MInvoice(A_Ctx, invoiceId, A_TrxName)
    
    if (!invoice.isSOTrx()) {
        // Only process sales invoices (customer invoices)
        log.fine("Skipping purchase invoice ID ${invoiceId}")
        return ""
    }
    
    Integer bpartnerId = invoice.getC_BPartner_ID()
    java.sql.Timestamp dateInvoiced = invoice.getDateInvoiced()
    
    if (bpartnerId == null || bpartnerId <= 0) {
        log.warning("Invalid C_BPartner_ID for invoice ${invoiceId}")
        return "Error: Invalid Business Partner ID"
    }
    
    // Call invoiceopen function to get the current open amount
    BigDecimal openAmt = null
    String selectSql = "SELECT invoiceopen(?, NULL::numeric) as openamt"
    PreparedStatement pstmt = null
    ResultSet rs = null
    
    try {
        pstmt = DB.prepareStatement(selectSql, A_TrxName)
        pstmt.setInt(1, invoiceId)
        rs = pstmt.executeQuery()
        
        if (rs.next()) {
            openAmt = rs.getBigDecimal("openamt")
        }
    } catch (SQLException e) {
        log.log(Level.SEVERE, "Error calling invoiceopen function for invoice ${invoiceId}", e)
        return "Error: Failed to calculate open amount"
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
        INSERT INTO tmp_invoice_open (c_invoice_id, c_bpartner_id, dateinvoiced, openamt)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (c_invoice_id) 
        DO UPDATE SET 
            c_bpartner_id = EXCLUDED.c_bpartner_id,
            dateinvoiced = EXCLUDED.dateinvoiced,
            openamt = EXCLUDED.openamt
    """
    
    PreparedStatement upsertStmt = null
    
    try {
        upsertStmt = DB.prepareStatement(upsertSql, A_TrxName)
        upsertStmt.setInt(1, invoiceId)
        upsertStmt.setInt(2, bpartnerId)
        upsertStmt.setTimestamp(3, dateInvoiced)
        upsertStmt.setBigDecimal(4, openAmt)
        
        int result = upsertStmt.executeUpdate()
        
        log.info("Successfully updated tmp_invoice_open for invoice ${invoiceId} (openamt: ${openAmt}, rows affected: ${result})")
        
    } catch (SQLException e) {
        log.log(Level.SEVERE, "Error upserting to tmp_invoice_open for invoice ${invoiceId}", e)
        return "Error: Failed to update tmp_invoice_open table: " + e.getMessage()
    } finally {
        DB.close(upsertStmt)
    }
    
    // Success
    return ""
    
} catch (Exception e) {
    log.log(Level.SEVERE, "Unexpected error in C_Invoice model validator", e)
    return "Error: " + e.getMessage()
}
