package it.easycomanda.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.graphics.drawable.GradientDrawable;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.content.res.Configuration;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static class Product {
        String name, category; int cents;
        Product(String n,String c,int p){name=n;category=c;cents=p;}
    }
    static class Line {
        String name; int quantity, unitCents;
        Line(String n,int q,int c){name=n;quantity=q;unitCents=c;}
    }
    private final List<Product> products=new ArrayList<>();
    private final List<Line> cart=new ArrayList<>();
    private final Set<String> printed=new HashSet<>();
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private OrderStore store;
    private LinearLayout layout,categoryPane,productPane,cartPane,sideContent;
    private boolean keypadVisible=false;
    private boolean portraitCash=false;
    private TextView display, totalDisplay;
    private boolean closingBusy=false;
    private boolean onCashScreen=false, saleBusy=false;
    private String lastProduct="", lastDetail="";
    private int lastCents=0;
    private static final int EXPORT_CSV=801;
    private String currentCategory="", entry="";
    private boolean multiply=false;
    private int orderNumber;
    private static final int[] CATEGORY_PALETTE={0xFFF4D5A5,0xFFB9DDF5,0xFFE7C5F1,0xFFBCE5C8,0xFFFFD5CB,0xFFF5E7A5,0xFFBFD9D4,0xFFD4D0F7};
    private final Map<String,Integer> categoryColors=new HashMap<>();
    private int categoryColor(String category){
        Integer saved=categoryColors.get(category);if(saved!=null)return saved;
        int index=new Random().nextInt(CATEGORY_PALETTE.length);
        int color=CATEGORY_PALETTE[index];categoryColors.put(category,color);saveCategoryColors();return color;
    }
    private void loadCategoryColors(){
        try{JSONObject obj=new JSONObject(prefs.getString("category_colors","{}"));Iterator<String> keys=obj.keys();while(keys.hasNext()){String k=keys.next();categoryColors.put(k,obj.getInt(k));}}catch(Exception ignored){}
        for(Product p:products)categoryColor(p.category);
    }
    private void saveCategoryColors(){JSONObject obj=new JSONObject();try{for(Map.Entry<String,Integer> e:categoryColors.entrySet())obj.put(e.getKey(),e.getValue());}catch(Exception ignored){}prefs.edit().putString("category_colors",obj.toString()).apply();}
    private void resetCart(){cart.clear();printed.clear();entry="";multiply=false;lastProduct="";lastDetail="";lastCents=0;orderNumber++;prefs.edit().putInt("order",orderNumber).commit();home();}
    private String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.ITALY).format(new Date());}


    @Override public void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("easycomanda",MODE_PRIVATE);store=new OrderStore(this);orderNumber=prefs.getInt("order",1);loadProducts();loadCategoryColors();purgeExpiredStatistics();home();}
    // Rimuove soltanto le giornate scadute, una volta per giorno locale.
    // La sessione di cassa in corso viene sempre preservata.
    private void purgeExpiredStatistics(){
        String date=today();
        if(date.equals(prefs.getString("last_retention_check","")))return;
        try{
            store.purgeExpiredDays();
            prefs.edit().putString("last_retention_check",date).apply();
        }catch(Exception e){android.util.Log.e("EasyComanda","Pulizia statistiche non riuscita",e);}
    }
    @Override protected void onResume(){super.onResume();if(prefs!=null&&store!=null)purgeExpiredStatistics();}
    private void loadProducts(){
        try{String raw=prefs.getString("products",null);if(raw!=null){JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++){JSONObject p=a.getJSONObject(i);products.add(new Product(p.getString("name"),p.getString("category"),p.getInt("cents")));}return;}}catch(Exception ignored){}
        products.add(new Product("Frico","Cucina",1000));products.add(new Product("Patatine","Cucina",400));products.add(new Product("Birra media","Bevande",500));products.add(new Product("Acqua","Bevande",150));products.add(new Product("Tiramisù","Dolci",450));saveProducts();
    }
    private void saveProducts(){JSONArray a=new JSONArray();for(Product p:products){JSONObject o=new JSONObject();try{o.put("name",p.name);o.put("category",p.category);o.put("cents",p.cents);}catch(Exception ignored){}a.put(o);}prefs.edit().putString("products",a.toString()).apply();}
    private String euro(int cents){return String.format(Locale.ITALY,"%.2f €",cents/100.0);}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private TextView label(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setPadding(dp(7),dp(8),dp(7),dp(8));return v;}
    private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setOnClickListener(v->r.run());return b;}
    private GradientDrawable bg(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private void styleButton(Button b){b.setBackground(bg(Color.rgb(244,245,246),Color.rgb(220,223,226),6));b.setTextColor(Color.rgb(28,35,38));b.setAllCaps(false);b.setTextSize(16);}
    private LinearLayout.LayoutParams spaced(int w,int h,int weight){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h,weight);p.setMargins(dp(3),dp(3),dp(3),dp(3));return p;}
    private void addButton(String s,Runnable r){layout.addView(button(s,r));}
    private void page(String title){onCashScreen=false;ScrollView scroll=new ScrollView(this);layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(12),dp(12),dp(12),dp(12));scroll.addView(layout);setContentView(scroll);TextView h=label(title,23);h.setTypeface(null,Typeface.BOLD);layout.addView(h);}
    private Product find(String n){for(Product p:products)if(p.name.equals(n))return p;return null;}
    private int total(){int t=0;for(Line l:cart)t+=l.quantity*l.unitCents;return t;}
    private String items(boolean prices){StringBuilder b=new StringBuilder();for(Line l:cart){b.append(l.quantity).append(" x ").append(l.name);if(prices)b.append("  ").append(euro(l.unitCents*l.quantity));b.append('\n');}return b.toString();}
    @Override public void onConfigurationChanged(Configuration config){
        super.onConfigurationChanged(config);
        if(onCashScreen)home();
    }
    private void home(){onCashScreen=true;
        if(getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT){homePortrait();return;}
        portraitCash=false;
        if(currentCategory.isEmpty()&&!products.isEmpty())currentCategory=products.get(0).category;
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(dp(9),dp(6),dp(9),dp(6));outer.setBackgroundColor(Color.rgb(250,250,250));setContentView(outer);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);outer.addView(header);
        TextView title=label("CheBala..! 1.5.8  ·  #"+String.format(Locale.ITALY,"%04d",orderNumber),17);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        Button togglePad=button("TASTIERINO",()->{keypadVisible=!keypadVisible;showSideContent();});styleButton(togglePad);togglePad.setTextSize(13);header.addView(togglePad,spaced(-2,dp(45),0));
        Button report=button("CHIUSURA SERALE",this::eveningClose);styleButton(report);header.addView(report,spaced(-2,dp(45),0));

        Button stats=button("STATISTICHE",this::requestStatisticsPin);styleButton(stats);header.addView(stats,spaced(-2,dp(45),0));
        Button settings=button("⚙",this::settings);styleButton(settings);header.addView(settings,spaced(dp(55),dp(45),0));
        LinearLayout top=new LinearLayout(this);outer.addView(top,new LinearLayout.LayoutParams(-1,0,5));
        ScrollView catsScroll=new ScrollView(this);categoryPane=new LinearLayout(this);categoryPane.setOrientation(LinearLayout.VERTICAL);catsScroll.addView(categoryPane);top.addView(catsScroll,new LinearLayout.LayoutParams(0,-1,1.05f));
        ScrollView prodScroll=new ScrollView(this);productPane=new LinearLayout(this);productPane.setOrientation(LinearLayout.VERTICAL);prodScroll.addView(productPane);top.addView(prodScroll,new LinearLayout.LayoutParams(0,-1,2.75f));
        LinearLayout pad=new LinearLayout(this);pad.setOrientation(LinearLayout.VERTICAL);top.addView(pad,new LinearLayout.LayoutParams(0,-1,2.0f));
        LinearLayout screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);screen.setBackground(bg(Color.rgb(24,38,43),0,8));screen.setPadding(dp(9),dp(5),dp(9),dp(5));pad.addView(screen,spaced(-1,dp(116),0));
        display=label("",17);display.setTextColor(Color.rgb(127,245,180));display.setTypeface(null,Typeface.BOLD);display.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);display.setPadding(dp(3),0,dp(3),0);display.setMaxLines(3);screen.addView(display,new LinearLayout.LayoutParams(-1,0,1));
        View rule=new View(this);rule.setBackgroundColor(Color.rgb(95,170,132));screen.addView(rule,new LinearLayout.LayoutParams(-1,dp(1)));
        totalDisplay=label("",18);totalDisplay.setTextColor(Color.rgb(127,245,180));totalDisplay.setTypeface(null,Typeface.BOLD);totalDisplay.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);totalDisplay.setSingleLine(true);totalDisplay.setTextSize(16);totalDisplay.setPadding(dp(2),0,dp(2),0);screen.addView(totalDisplay,new LinearLayout.LayoutParams(-1,dp(38)));
        sideContent=new LinearLayout(this);
        sideContent.setOrientation(LinearLayout.VERTICAL);
        pad.addView(sideContent,new LinearLayout.LayoutParams(-1,0,1));
        showSideContent();
        LinearLayout actions=new LinearLayout(this);outer.addView(actions);
        Button receipt=button("SCONTRINO",this::receipt);receipt.setTextColor(Color.WHITE);receipt.setBackground(bg(Color.rgb(32,92,72),0,6));receipt.setTextSize(19);actions.addView(receipt,spaced(0,dp(60),2));
        Button clear=button("ANNULLA CONTO",this::newOrder);styleButton(clear);actions.addView(clear,spaced(0,dp(60),1));
        renderCategories();renderProducts();renderCart();updateDisplay();
    }
    // Smartphone: visualizzazione verticale, senza alterare conto o database.
    private void homePortrait(){
        portraitCash=true;
        if(currentCategory.isEmpty()&&!products.isEmpty())currentCategory=products.get(0).category;
        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(6),dp(4),dp(6),dp(4));
        outer.setBackgroundColor(Color.rgb(250,250,250));
        setContentView(outer);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        outer.addView(header,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView title=label("CheBala..! 1.5.8 · #"+String.format(Locale.ITALY,"%04d",orderNumber),15);
        title.setTypeface(null,Typeface.BOLD);title.setSingleLine(true);
        header.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        Button settings=button("⚙",this::settings);styleButton(settings);
        header.addView(settings,spaced(dp(48),dp(43),0));
        LinearLayout displayBox=new LinearLayout(this);displayBox.setOrientation(LinearLayout.VERTICAL);
        displayBox.setBackground(bg(Color.rgb(24,38,43),0,8));displayBox.setPadding(dp(9),dp(3),dp(9),dp(3));
        outer.addView(displayBox,new LinearLayout.LayoutParams(-1,dp(96)));
        display=label("",14);display.setTextColor(Color.rgb(127,245,180));
        display.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);display.setMaxLines(2);
        displayBox.addView(display,new LinearLayout.LayoutParams(-1,0,1));
        totalDisplay=label("",21);totalDisplay.setTypeface(null,Typeface.BOLD);
        totalDisplay.setTextColor(Color.rgb(127,245,180));totalDisplay.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        totalDisplay.setSingleLine(true);displayBox.addView(totalDisplay,new LinearLayout.LayoutParams(-1,dp(39)));
        LinearLayout commands=new LinearLayout(this);
        outer.addView(commands,new LinearLayout.LayoutParams(-1,dp(49)));
        Button togglePad=button("TASTIERINO",()->{keypadVisible=!keypadVisible;showSideContent();});
        styleButton(togglePad);togglePad.setTextSize(11);commands.addView(togglePad,spaced(0,-1,1));
        Button stats=button("STATISTICHE",this::requestStatisticsPin);styleButton(stats);
        stats.setTextSize(11);commands.addView(stats,spaced(0,-1,1));
        Button report=button("CHIUSURA",this::eveningClose);styleButton(report);
        report.setTextSize(11);commands.addView(report,spaced(0,-1,1));
        HorizontalScrollView catsScroll=new HorizontalScrollView(this);
        catsScroll.setHorizontalScrollBarEnabled(false);
        categoryPane=new LinearLayout(this);categoryPane.setOrientation(LinearLayout.HORIZONTAL);
        catsScroll.addView(categoryPane);outer.addView(catsScroll,new LinearLayout.LayoutParams(-1,dp(61)));
        ScrollView prodScroll=new ScrollView(this);prodScroll.setFillViewport(false);
        productPane=new LinearLayout(this);productPane.setOrientation(LinearLayout.VERTICAL);
        prodScroll.addView(productPane);
        outer.addView(prodScroll,new LinearLayout.LayoutParams(-1,0,1));
        sideContent=new LinearLayout(this);sideContent.setOrientation(LinearLayout.VERTICAL);
        outer.addView(sideContent,new LinearLayout.LayoutParams(-1,dp(200)));
        showSideContent();
        LinearLayout actions=new LinearLayout(this);outer.addView(actions,new LinearLayout.LayoutParams(-1,dp(60)));
        Button receipt=button("SCONTRINO",this::receipt);receipt.setTextColor(Color.WHITE);
        receipt.setBackground(bg(Color.rgb(32,92,72),0,6));receipt.setTextSize(17);
        actions.addView(receipt,spaced(0,-1,2));
        Button clear=button("ANNULLA CONTO",this::newOrder);styleButton(clear);
        clear.setTextSize(12);actions.addView(clear,spaced(0,-1,1));
        renderCategories();renderProducts();renderCart();updateDisplay();
    }
    private void showSideContent(){
        if(sideContent==null)return;
        sideContent.removeAllViews();
        if(keypadVisible){
            String[][] keys={{"7","8","9"},{"4","5","6"},{"1","2","3"},{",","0","⌫"}};
            for(String[] row:keys){
                LinearLayout r=new LinearLayout(this);
                sideContent.addView(r,new LinearLayout.LayoutParams(-1,0,1));
                for(String key:row){
                    TextView k=new TextView(this);
                    k.setText(key);k.setTextColor(Color.rgb(18,30,39));k.setTextSize(27);
                    k.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                    k.setGravity(Gravity.CENTER);
                    k.setBackground(bg(Color.rgb(235,238,241),Color.rgb(180,187,194),6));
                    k.setOnClickListener(v->keyPress(key));
                    r.addView(k,spaced(0,-1,1));
                    if(key.equals("⌫"))k.setOnLongClickListener(v->{entry="";multiply=false;updateDisplay();return true;});
                }
            }
            Button mult=button("× QUANTITÀ",()->{
                if(entry.isEmpty()){toast("Prima digita la quantità");return;}
                if(entry.contains(",")||entry.equals("0")){toast("Quantità intera maggiore di zero");return;}
                multiply=true;updateDisplay();
            });
            styleButton(mult);
            sideContent.addView(mult,spaced(-1,dp(portraitCash?37:55),0));
        }else{
            TextView countTitle=label("CONTO (tocca una voce per modificarla)",14);
            countTitle.setTypeface(null,Typeface.BOLD);
            sideContent.addView(countTitle);
            ScrollView cartScroll=new ScrollView(this);
            cartPane=new LinearLayout(this);
            cartPane.setOrientation(LinearLayout.VERTICAL);
            cartScroll.addView(cartPane);
            sideContent.addView(cartScroll,new LinearLayout.LayoutParams(-1,0,1));
            renderCart();
        }
    }
    private void renderCategories(){
        categoryPane.removeAllViews();LinkedHashSet<String> cats=new LinkedHashSet<>();
        for(Product p:products)cats.add(p.category);
        if(!cats.contains(currentCategory)&&!cats.isEmpty())currentCategory=cats.iterator().next();
        for(String c:cats){
            Button b=button(c,()->{currentCategory=c;renderCategories();renderProducts();});
            if(c.equals(currentCategory))b.setTypeface(null,Typeface.BOLD);
            styleButton(b);b.setBackground(bg(categoryColor(c),c.equals(currentCategory)?Color.rgb(35,80,90):Color.rgb(210,210,210),6));
            if(portraitCash){b.setTextSize(13);categoryPane.addView(b,spaced(-2,dp(53),0));}
            else categoryPane.addView(b,spaced(-1,dp(62),0));
        }
    }
    private void renderProducts(){
        productPane.removeAllViews();
        LinearLayout row=null;
        int count=0;
        for(Product p:products)if(p.category.equals(currentCategory)){
            if(count%2==0){
                row=new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                productPane.addView(row,new LinearLayout.LayoutParams(-1,dp(portraitCash?76:84)));
            }
            Button b=button(p.name+"\n"+euro(p.cents),()->addProduct(p));
            styleButton(b);
            b.setTextSize(portraitCash?14:15);
            b.setBackground(bg(categoryColor(p.category),Color.rgb(210,210,210),6));
            row.addView(b,spaced(0,-1,1));
            count++;
        }
        if(count%2==1 && row!=null){
            View blank=new View(this);
            row.addView(blank,spaced(0,-1,1));
        }
    }
    private void updateDisplay(){if(display==null)return;
        String detail;
        if(!entry.isEmpty())detail=(multiply?"QUANTITÀ":"PREZZO DIGITATO")+"\n"+entry+(multiply?" ×":" €");
        else if(!lastProduct.isEmpty())detail=lastProduct+"\n"+lastDetail+"\n"+euro(lastCents);
        else detail="PRONTO";
        display.setText(detail);
        if(totalDisplay!=null)totalDisplay.setText("TOTALE CONTO  "+euro(total()));
    }
    private void keyPress(String key){
        if(key.equals("⌫")){if(!entry.isEmpty())entry=entry.substring(0,entry.length()-1);if(entry.isEmpty())multiply=false;updateDisplay();return;}
        if(key.equals(",")){if(!multiply&&!entry.contains(","))entry=entry.isEmpty()?"0,":entry+",";updateDisplay();return;}
        if(entry.length()>=9)return;
        if(entry.equals("0")&&!key.equals("0"))entry=key;else if(entry.isEmpty()||!entry.equals("0")||!key.equals("0"))entry+=key;
        updateDisplay();
    }
    private void addProduct(Product p){int quantity=1,price=p.cents;
        if(!entry.isEmpty()){
            if(multiply){try{quantity=Integer.parseInt(entry);if(quantity<1||quantity>999){toast("Quantità da 1 a 999");return;}}catch(Exception e){toast("Quantità non valida");return;}}
            else{try{price=new BigDecimal(entry.replace(',','.')).movePointRight(2).intValueExact();if(price<0||price>10000000){toast("Prezzo non valido");return;}}catch(Exception e){toast("Prezzo non valido: massimo due decimali");return;}}
        }
        if((long)quantity*price>Integer.MAX_VALUE){toast("Importo troppo elevato");return;}
        Line existing=null;for(Line line:cart)if(line.name.equals(p.name)&&line.unitCents==price){existing=line;break;}
        if(existing!=null){if(existing.quantity>99999-quantity){toast("Quantità troppo elevata");return;}existing.quantity+=quantity;}
        else cart.add(new Line(p.name,quantity,price));lastProduct=p.name;lastDetail=quantity+" × "+euro(price);lastCents=quantity*price;entry="";multiply=false;updateDisplay();renderCart();
    }
    private void renderCart(){if(cartPane==null)return;cartPane.removeAllViews();for(Line l:new ArrayList<>(cart)){Button b=button(l.quantity+" × "+l.name+"  ·  "+euro(l.unitCents)+" cad.  =  "+euro(l.quantity*l.unitCents),()->editLine(l));b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);styleButton(b);cartPane.addView(b,spaced(-1,dp(portraitCash?44:51),0));}updateDisplay();}
    private void editLine(Line l){new AlertDialog.Builder(this).setTitle(l.name).setMessage(l.quantity+" × "+euro(l.unitCents)+" = "+euro(l.quantity*l.unitCents)).setPositiveButton("Elimina",(d,w)->{cart.remove(l);printed.clear();renderCart();}).setNeutralButton("Quantità",(d,w)->{
        EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setText(String.valueOf(l.quantity));new AlertDialog.Builder(this).setTitle("Nuova quantità").setView(input).setPositiveButton("Salva",(a,b)->{try{int q=Integer.parseInt(input.getText().toString());if(q<1||q>999)throw new Exception();l.quantity=q;printed.clear();renderCart();}catch(Exception e){toast("Quantità non valida");}}).setNegativeButton("Annulla",null).show();
    }).setNegativeButton("Annulla",null).show();}
    private void undoLastReceipt(){
        if(saleBusy||closingBusy){toast("Attendi la fine dell'operazione");return;}
        final OrderStore.LastSale last;
        try{last=store.lastOpenSessionSale();}
        catch(Exception e){toast("Impossibile leggere l'ultimo scontrino");return;}
        if(last==null){toast("Nessuno scontrino da annullare nella sessione corrente");return;}
        String when=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.ITALY).format(new Date(last.createdAt));
        new AlertDialog.Builder(this)
            .setTitle("ANNULLA ULTIMO SCONTRINO")
            .setMessage("Scontrino #"+last.number+"\n"+when+"\nImporto: "+euro(last.cents)
                +"\n\nLa vendita sarà eliminata definitivamente dalle statistiche."
                +"\nLo scontrino cartaceo già stampato non può essere ritirato.")
            .setNegativeButton("NO",null)
            .setPositiveButton("SÌ, ANNULLA",(dialog,which)->{
                if(saleBusy||closingBusy){toast("Operazione in corso");return;}
                try{store.undoLastSale(last.id);toast("Ultimo scontrino annullato dalle statistiche");home();}
                catch(Exception e){new AlertDialog.Builder(this).setTitle("Annullamento non riuscito")
                    .setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
            }).show();
    }
    private void newOrder(){new AlertDialog.Builder(this).setTitle("Annullare il conto?").setMessage("Eliminare tutti i prodotti senza registrare la vendita?").setPositiveButton("ANNULLA CONTO",(d,w)->resetCart()).setNegativeButton("No",null).show();}
    private String receiptText(){return "CheBala..!\nSCONTRINO NON FISCALE\nCONTO #"+String.format(Locale.ITALY,"%04d",orderNumber)+"\n"+new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.ITALY).format(new Date())+"\n--------------------------------\n"+items(true)+"--------------------------------\nTOTALE: "+euro(total())+"\nDocumento privo di valore fiscale\n";}
    private void receipt(){
        if(cart.isEmpty()){toast("Il conto è vuoto");return;}
        if(saleBusy){toast("Operazione già in corso");return;}
        executeReceipt();
    }
    private void executeReceipt(){
        if(saleBusy)return;saleBusy=true;
        final String content=receiptText();final int number=orderNumber;final int amount=total();
        final JSONArray rows=new JSONArray();try{for(Line l:cart){JSONObject o=new JSONObject();o.put("name",l.name);o.put("quantity",l.quantity);o.put("unit_cents",l.unitCents);rows.put(o);}}catch(Exception e){saleBusy=false;toast("Errore preparazione conto");return;}
        if(prefs.getBoolean("virtual",true)){
            saveSale(number,amount,rows.toString());
            return;
        }
        final String host=prefs.getString("ip","192.168.1.100");final int port=prefs.getInt("port",9100);
        executor.execute(()->{
            try{PrinterClient.send(host,port,PrinterClient.document(content,prefs.getBoolean("cut",true)));
                runOnUiThread(()->saveSale(number,amount,rows.toString()));
            }catch(Exception e){runOnUiThread(()->{saleBusy=false;new AlertDialog.Builder(this).setTitle("Stampa non confermata").setMessage("Il conto è rimasto aperto e la vendita NON è stata registrata.\nControlla se lo scontrino è uscito prima di riprovare: "+e.getMessage()).setPositiveButton("OK",null).show();});}
        });
    }
    private void saveSale(int number,int amount,String rows){
        try{purgeExpiredStatistics();store.closeSale(number,amount,rows);saleBusy=false;resetCart();toast("Vendita registrata");}
        catch(Exception e){saleBusy=false;new AlertDialog.Builder(this).setTitle("Vendita NON registrata").setMessage("La stampa potrebbe essere stata inviata. NON ristampare senza controllare.\n"+e.getMessage()).setPositiveButton("OK",null).show();}
    }
    private String reportText(String day){
        StringBuilder sb=new StringBuilder("CheBala..! - RESOCONTO\n"+day+"\nConti: "+store.dayCount(day)+"\nPezzi: "+store.dayQuantity(day)+"\n-------------------------------\n");
        for(OrderStore.Summary row:store.summary(day))sb.append(row.name).append("  x").append(row.quantity).append("  ").append(euro(row.cents)).append('\n');
        sb.append("-------------------------------\nTOTALE ").append(euro(store.dayTotal(day))).append('\n');return sb.toString();
    }
    private void printReport(String day){
        String contents=reportText(day);
        if(prefs.getBoolean("virtual",true)){new AlertDialog.Builder(this).setTitle("Anteprima resoconto "+day).setMessage(contents).setPositiveButton("OK",null).show();return;}
        String host=prefs.getString("ip","192.168.1.100");int port=prefs.getInt("port",9100);
        executor.execute(()->{try{PrinterClient.send(host,port,PrinterClient.document(contents,prefs.getBoolean("cut",true)));runOnUiThread(()->toast("Resoconto inviato"));}catch(Exception e){runOnUiThread(()->toast("Errore: "+e.getMessage()));}});
    }
    private String sessionText(long from,long to){
        StringBuilder sb=new StringBuilder("CheBala..! - CHIUSURA SERALE\n");
        SimpleDateFormat fmt=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.ITALY);
        sb.append("Dal ").append(fmt.format(new Date(from))).append("\nAl  ").append(fmt.format(new Date(to))).append("\n");
        sb.append("Conti: ").append(store.sessionCount(from,to)).append("\nPezzi: ").append(store.sessionQuantity(from,to)).append("\n-------------------------------\n");
        for(OrderStore.Summary row:store.sessionSummary(from,to))sb.append(row.name).append("  x").append(row.quantity).append("  ").append(euro(row.cents)).append('\n');
        sb.append("-------------------------------\nTOTALE: ").append(euro(store.sessionTotal(from,to))).append('\n');
        return sb.toString();
    }
    private void eveningClose(){
        if(closingBusy||saleBusy){toast("Attendi la fine dell'operazione");return;}
        final long from=store.sessionStart();final long until=System.currentTimeMillis();
        if(store.sessionCount(from,until)==0){toast("Nessuna vendita da chiudere");return;}
        final String contents=sessionText(from,until);
        new AlertDialog.Builder(this).setTitle("CHIUSURA SERALE")
            .setMessage("Stampare e azzerare la sessione di cassa? Le statistiche per data resteranno invariate.\n\n"+contents)
            .setPositiveButton("CONFERMA CHIUSURA",(d,w)->executeEveningClose(from,until,contents))
            .setNegativeButton("Annulla",null).show();
    }
    private void executeEveningClose(long from,long until,String contents){
        if(closingBusy)return;closingBusy=true;
        if(prefs.getBoolean("virtual",true)){
            new AlertDialog.Builder(this).setTitle("ANTEPRIMA CHIUSURA SERALE").setMessage(contents)
                .setPositiveButton("Conferma chiusura simulata",(d,w)->finishEveningClose(from,until))
                .setNegativeButton("Annulla",(d,w)->closingBusy=false)
                .setOnCancelListener(d->closingBusy=false).show();return;
        }
        final String host=prefs.getString("ip","192.168.1.100");final int port=prefs.getInt("port",9100);
        executor.execute(()->{try{
            PrinterClient.send(host,port,PrinterClient.document(contents,prefs.getBoolean("cut",true)));
            runOnUiThread(()->finishEveningClose(from,until));
        }catch(Exception e){runOnUiThread(()->{closingBusy=false;new AlertDialog.Builder(this).setTitle("Chiusura NON registrata")
            .setMessage("Controlla se il resoconto è uscito prima di riprovare. Sessione non azzerata.\n"+e.getMessage()).setPositiveButton("OK",null).show();});}});
    }
    private void finishEveningClose(long from,long until){
        try{store.closeSession(from,until);closingBusy=false;toast("Chiusura serale registrata");home();}
        catch(Exception e){closingBusy=false;new AlertDialog.Builder(this).setTitle("Chiusura NON salvata").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
    }
    private void requestStatisticsPin(){
        final EditText pin=new EditText(this);
        pin.setSingleLine(true);
        pin.setHint("PIN di 6 cifre");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        LinearLayout wrapper=new LinearLayout(this);
        wrapper.setPadding(dp(24),dp(12),dp(24),dp(4));
        wrapper.addView(pin,new LinearLayout.LayoutParams(-1,dp(56)));
        new AlertDialog.Builder(this)
            .setTitle("Accesso alle statistiche")
            .setMessage("Inserisci il PIN per visualizzare le statistiche.")
            .setView(wrapper)
            .setNegativeButton("ANNULLA",(dialog,which)->{})
            .setPositiveButton("ACCEDI",(dialog,which)->{
                if("147896".equals(pin.getText().toString()))statistics();
                else toast("PIN errato");
            }).show();
    }
    private void statistics(){
        page("Statistiche vendite");
        addButton("← Torna alla cassa",this::home);
        addButton("Esporta archivio CSV",()->{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/csv");intent.putExtra(Intent.EXTRA_TITLE,"EasyComanda_vendite.csv");startActivityForResult(intent,EXPORT_CSV);
        });
        List<String> days=store.days();
        if(days.isEmpty()){layout.addView(label("Nessun conto chiuso. Le stampe non vengono conteggiate.",16));return;}
        for(String day:days){
            Button b=button(day+"  ·  "+euro(store.dayTotal(day))+"  ·  "+store.dayCount(day)+" conti",()->showDay(day));
            styleButton(b);layout.addView(b,new LinearLayout.LayoutParams(-1,dp(60)));
        }
    }
    private void showDay(String day){
        page("Vendite del "+day);addButton("← Tutte le giornate",this::statistics);
        layout.addView(label("Conti chiusi: "+store.dayCount(day)+"\nIncasso: "+euro(store.dayTotal(day))+"\nPezzi venduti: "+store.dayQuantity(day),20));
        layout.addView(label("PRODOTTO                           QUANTITÀ        TOTALE",15));
        for(OrderStore.Summary row:store.summary(day)){
            layout.addView(label(row.name+"     × "+row.quantity+"     "+euro(row.cents),18));
        }
        addButton("STAMPA RESOCONTO",()->printReport(day));
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==EXPORT_CSV&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new Exception("Impossibile aprire il file");
                out.write(store.exportCsv().getBytes(StandardCharsets.UTF_8));toast("CSV esportato");
            }catch(Exception e){toast("Errore esportazione: "+e.getMessage());}
        }
    }
    private void settings(){page("Impostazioni");Button back=button("⬅ TORNA ALLA CASSA",this::home);styleButton(back);back.setTextSize(21);back.setTypeface(null,Typeface.BOLD);back.setBackground(bg(Color.rgb(188,229,200),0,8));layout.addView(back,new LinearLayout.LayoutParams(-1,dp(70)));Switch virtual=new Switch(this);virtual.setText("Stampante virtuale (anteprima nell'app)");virtual.setChecked(prefs.getBoolean("virtual",true));virtual.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("virtual",on).apply());layout.addView(virtual);
        layout.addView(label("Stampante Ethernet (usata quando la virtuale è disattivata)",16));EditText ip=new EditText(this);ip.setSingleLine(true);ip.setText(prefs.getString("ip","192.168.1.100"));ip.setHint("IP stampante");layout.addView(ip);
        EditText port=new EditText(this);port.setInputType(InputType.TYPE_CLASS_NUMBER);port.setText(String.valueOf(prefs.getInt("port",9100)));port.setHint("Porta TCP");layout.addView(port);
        addButton("Salva indirizzo e porta",()->{int p;try{p=Integer.parseInt(port.getText().toString());}catch(Exception e){toast("Porta non valida");return;}if(p<1||p>65535){toast("Porta non valida");return;}prefs.edit().putString("ip",ip.getText().toString().trim()).putInt("port",p).apply();toast("Salvato");});
        addButton("Stampa di prova",()->{if(prefs.getBoolean("virtual",true)){new AlertDialog.Builder(this).setTitle("Stampa di prova virtuale").setMessage("CheBala..! - TEST\nStampante virtuale attiva\n").setPositiveButton("OK",null).show();return;}String host=ip.getText().toString().trim();int p;try{p=Integer.parseInt(port.getText().toString());}catch(Exception e){toast("Porta non valida");return;}final int target=p;executor.execute(()->{try{PrinterClient.send(host,target,PrinterClient.document("CheBala..! - TEST\n",prefs.getBoolean("cut",true)));runOnUiThread(()->toast("Test inviato"));}catch(Exception e){runOnUiThread(()->toast("Errore: "+e.getMessage()));}});});
        Switch cut=new Switch(this);cut.setText("Taglio automatico ESC/POS (se compatibile)");cut.setChecked(prefs.getBoolean("cut",true));cut.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("cut",on).apply());layout.addView(cut);
        layout.addView(label("Prodotti · tocca per eliminare",20));for(Product p:new ArrayList<>(products))addButton(p.category+" · "+p.name+" · "+euro(p.cents),()->new AlertDialog.Builder(this).setTitle("Eliminare "+p.name+"?").setPositiveButton("Elimina",(d,w)->{for(Line l:cart)if(l.name.equals(p.name)){toast("Prima rimuovi il prodotto dal conto");return;}products.remove(p);saveProducts();settings();}).setNegativeButton("Annulla",null).show());
        EditText name=new EditText(this);name.setHint("Nome prodotto");layout.addView(name);EditText price=new EditText(this);price.setHint("Prezzo es. 4,50");price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);layout.addView(price);EditText cat=new EditText(this);cat.setHint("Categoria es. Dolci");layout.addView(cat);
        addButton("Aggiungi prodotto",()->{String n=name.getText().toString().trim(),c=cat.getText().toString().trim();int cents;try{cents=new BigDecimal(price.getText().toString().replace(',','.')).movePointRight(2).intValueExact();}catch(Exception e){toast("Prezzo non valido");return;}if(n.isEmpty()||c.isEmpty()||cents<0||find(n)!=null){toast("Controlla nome, categoria e prezzo");return;}products.add(new Product(n,c,cents));saveProducts();categoryColor(c);settings();});
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){
        if(!onCashScreen){home();return;}
        new AlertDialog.Builder(this).setTitle("Uscire da EasyComanda?")
          .setMessage(cart.isEmpty()?"Sei sicuro di voler uscire?":"Sei sicuro di voler uscire? Il conto aperto non è ancora stato registrato.")
          .setPositiveButton("SÌ, ESCI",(d,w)->finish()).setNegativeButton("NO",null).show();
    }
    @Override protected void onDestroy(){executor.shutdown();store.close();super.onDestroy();}
}
