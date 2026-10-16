package it.easycomanda.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public class OrderStore extends SQLiteOpenHelper {
    public OrderStore(Context context){super(context,"easycomanda.db",null,3);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT, order_number INTEGER NOT NULL, created_at INTEGER NOT NULL, contents TEXT NOT NULL, total_cents INTEGER NOT NULL, kind TEXT NOT NULL, sent INTEGER NOT NULL)");
        createSales(db);
        createClosures(db);
    }
    private void createSales(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS sales (id INTEGER PRIMARY KEY AUTOINCREMENT, order_number INTEGER NOT NULL UNIQUE, created_at INTEGER NOT NULL, day TEXT NOT NULL, total_cents INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sale_items (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL, name TEXT NOT NULL, quantity INTEGER NOT NULL, unit_cents INTEGER NOT NULL, FOREIGN KEY(sale_id) REFERENCES sales(id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_day ON sales(day)");
    }
    private void createClosures(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS closures (id INTEGER PRIMARY KEY AUTOINCREMENT, start_at INTEGER NOT NULL, closed_at INTEGER NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){if(oldVersion<2)createSales(db);if(oldVersion<3)createClosures(db);}
    public void record(int number,String contents,int cents,String kind,boolean sent){
        ContentValues v=new ContentValues();v.put("order_number",number);v.put("created_at",System.currentTimeMillis());v.put("contents",contents);v.put("total_cents",cents);v.put("kind",kind);v.put("sent",sent?1:0);getWritableDatabase().insert("orders",null,v);
    }
    public void closeSale(int number,int cents,String json) throws Exception {
        SQLiteDatabase db=getWritableDatabase();JSONArray lines=new JSONArray(json);
        if(lines.length()==0)throw new Exception("Nessun prodotto");
        long now=System.currentTimeMillis();String day=new SimpleDateFormat("yyyy-MM-dd",Locale.ITALY).format(new Date(now));
        db.beginTransaction();try{
            ContentValues sale=new ContentValues();sale.put("order_number",number);sale.put("created_at",now);sale.put("day",day);sale.put("total_cents",cents);
            long id=db.insertOrThrow("sales",null,sale);
            for(int i=0;i<lines.length();i++){JSONObject row=lines.getJSONObject(i);ContentValues v=new ContentValues();v.put("sale_id",id);v.put("name",row.getString("name"));v.put("quantity",row.getInt("quantity"));v.put("unit_cents",row.getInt("unit_cents"));db.insertOrThrow("sale_items",null,v);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public static class LastSale {
        public final long id;
        public final int number, cents;
        public final long createdAt;
        LastSale(long id,int number,int cents,long createdAt){this.id=id;this.number=number;this.cents=cents;this.createdAt=createdAt;}
    }
    /** L'ultimo scontrino registrato, solo nella sessione di cassa corrente. */
    public LastSale lastOpenSessionSale(){
        long from=sessionStart();
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT id,order_number,total_cents,created_at FROM sales WHERE created_at>? ORDER BY id DESC LIMIT 1",
                new String[]{String.valueOf(from)})){
            if(!c.moveToFirst())return null;
            return new LastSale(c.getLong(0),c.getInt(1),c.getInt(2),c.getLong(3));
        }
    }
    /** Elimina atomicamente la vendita e le sue righe, solo se è ancora l'ultima. */
    public void undoLastSale(long expectedId){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            long from=sessionStart();
            long lastId=-1,lastTime=-1;
            try(Cursor c=db.rawQuery("SELECT id,created_at FROM sales ORDER BY id DESC LIMIT 1",null)){
                if(c.moveToFirst()){lastId=c.getLong(0);lastTime=c.getLong(1);}
            }
            if(lastId!=expectedId||lastTime<=from)
                throw new IllegalStateException("Lo scontrino non è più l'ultimo della sessione aperta");
            db.delete("sale_items","sale_id=?",new String[]{String.valueOf(expectedId)});
            if(db.delete("sales","id=?",new String[]{String.valueOf(expectedId)})!=1)
                throw new IllegalStateException("Scontrino non trovato");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public long sessionStart(){try(Cursor c=getReadableDatabase().rawQuery("SELECT MAX(closed_at) FROM closures",null)){
        if(c.moveToFirst()&&!c.isNull(0))return c.getLong(0);
        return 0L;
    }}
    public int sessionCount(long from,long until){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sales WHERE created_at>? AND created_at<=?",new String[]{String.valueOf(from),String.valueOf(until)})){return c.moveToFirst()?c.getInt(0):0;}}
    public int sessionTotal(long from,long until){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(total_cents),0) FROM sales WHERE created_at>? AND created_at<=?",new String[]{String.valueOf(from),String.valueOf(until)})){return c.moveToFirst()?c.getInt(0):0;}}
    public int sessionQuantity(long from,long until){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(i.quantity),0) FROM sale_items i JOIN sales s ON s.id=i.sale_id WHERE s.created_at>? AND s.created_at<=?",new String[]{String.valueOf(from),String.valueOf(until)})){return c.moveToFirst()?c.getInt(0):0;}}
    public List<Summary> sessionSummary(long from,long until){List<Summary> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT i.name,SUM(i.quantity),SUM(i.quantity*i.unit_cents) FROM sale_items i JOIN sales s ON s.id=i.sale_id WHERE s.created_at>? AND s.created_at<=? GROUP BY i.name ORDER BY i.name COLLATE NOCASE",new String[]{String.valueOf(from),String.valueOf(until)})){while(c.moveToNext())result.add(new Summary(c.getString(0),c.getInt(1),c.getInt(2)));}return result;}
    public void closeSession(long from,long until){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
        long actual=sessionStart();if(actual!=from)throw new IllegalStateException("La sessione è già stata chiusa");
        ContentValues v=new ContentValues();v.put("start_at",from);v.put("closed_at",until);db.insertOrThrow("closures",null,v);db.setTransactionSuccessful();
    }finally{db.endTransaction();}}
    /** Conservazione per data di calendario: il 16/10/2026 scade il 16/10/2027.
     * La cancellazione è transazionale e non interessa una sessione ancora aperta.
     * Le chiusure restano registrate per preservare l'inizio della sessione corrente.
     */
    public int purgeExpiredDays(){
        Calendar cal=Calendar.getInstance();
        cal.add(Calendar.YEAR,-1);
        String cutoff=new SimpleDateFormat("yyyy-MM-dd",Locale.ITALY).format(cal.getTime());
        long activeSessionStart=sessionStart();
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            String filter="day<=? AND created_at<=?";
            String[] args={cutoff,String.valueOf(activeSessionStart)};
            db.execSQL("DELETE FROM sale_items WHERE sale_id IN (SELECT id FROM sales WHERE "+filter+")",args);
            int removed=db.delete("sales",filter,args);
            // Vecchi record di stampa (tabella legacy) non necessari alle statistiche.
            // Non cancelliamo ordini della sessione corrente.
            db.delete("orders","created_at<=? AND created_at<=?",new String[]{
                String.valueOf(endOfCutoffDay(cutoff)),String.valueOf(activeSessionStart)});
            db.setTransactionSuccessful();
            return removed;
        }finally{db.endTransaction();}
    }
    private long endOfCutoffDay(String day){
        try{
            SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd",Locale.ITALY);
            fmt.setLenient(false);
            Calendar c=Calendar.getInstance();c.setTime(fmt.parse(day));
            c.add(Calendar.DAY_OF_MONTH,1);
            return c.getTimeInMillis()-1L;
        }catch(Exception e){throw new IllegalStateException("Data scadenza non valida",e);}
    }
    public List<String> days(){List<String> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT day FROM sales ORDER BY day DESC",null)){while(c.moveToNext())result.add(c.getString(0));}return result;}
    public int dayCount(String day){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sales WHERE day=?",new String[]{day})){return c.moveToFirst()?c.getInt(0):0;}}
    public int dayTotal(String day){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(total_cents),0) FROM sales WHERE day=?",new String[]{day})){return c.moveToFirst()?c.getInt(0):0;}}
    public int dayQuantity(String day){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(i.quantity),0) FROM sale_items i JOIN sales s ON s.id=i.sale_id WHERE s.day=?",new String[]{day})){return c.moveToFirst()?c.getInt(0):0;}}
    public static class Summary {public String name;public int quantity,cents;Summary(String n,int q,int c){name=n;quantity=q;cents=c;}}
    public List<Summary> summary(String day){List<Summary> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT i.name,SUM(i.quantity),SUM(i.quantity*i.unit_cents) FROM sale_items i JOIN sales s ON s.id=i.sale_id WHERE s.day=? GROUP BY i.name ORDER BY i.name COLLATE NOCASE",new String[]{day})){while(c.moveToNext())result.add(new Summary(c.getString(0),c.getInt(1),c.getInt(2)));}return result;}
    private String csv(String value){return "\""+value.replace("\"","\"\"")+"\"";}
    public String exportCsv(){StringBuilder sb=new StringBuilder("data;ora;numero_conto;prodotto;quantita;prezzo_unitario_eur;totale_riga_eur\n");
        try(Cursor c=getReadableDatabase().rawQuery("SELECT s.created_at,s.order_number,i.name,i.quantity,i.unit_cents FROM sale_items i JOIN sales s ON s.id=i.sale_id ORDER BY s.created_at,s.id,i.id",null)){
            SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd",Locale.ITALY),time=new SimpleDateFormat("HH:mm:ss",Locale.ITALY);
            while(c.moveToNext()){Date d=new Date(c.getLong(0));int q=c.getInt(3),unit=c.getInt(4);sb.append(date.format(d)).append(';').append(time.format(d)).append(';').append(c.getInt(1)).append(';').append(csv(c.getString(2))).append(';').append(q).append(';').append(String.format(Locale.ITALY,"%.2f",unit/100.0)).append(';').append(String.format(Locale.ITALY,"%.2f",q*unit/100.0)).append('\n');}
        }return sb.toString();
    }
}
