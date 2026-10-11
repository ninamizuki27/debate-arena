package jp.ninamizuki.debatearena;
import android.app.*;import android.os.*;import android.graphics.Color;import android.view.*;import android.widget.*;import java.util.concurrent.*;
public class MainActivity extends Activity {
 LinearLayout screen,transcript;EditText subject,model;Spinner count;TextView status;Button start,login;SiwcClient client;ExecutorService exec=Executors.newSingleThreadExecutor();StringBuilder history=new StringBuilder();
 int bg=0xff101827,white=0xfff0f3fa;
 TextView label(String t,int size){TextView v=new TextView(this);v.setText(t);v.setTextColor(white);v.setTextSize(size);v.setPadding(8,12,8,12);return v;}
 void add(String text,int color){runOnUiThread(()->{TextView v=label(text,15);v.setTextIsSelectable(true);v.setBackgroundColor(color);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,8,0,8);transcript.addView(v,p);});}
 void msg(String text){runOnUiThread(()->status.setText(text));}
 @Override public void onCreate(Bundle b){super.onCreate(b);client=new SiwcClient(this);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(bg);screen=new LinearLayout(this);screen.setPadding(28,32,28,36);screen.setOrientation(1);scroll.addView(screen);setContentView(scroll);
 screen.addView(label("DEBATE ARENA",26));screen.addView(label("独立した賛成AIと反対AIが討論。第三者AIが判定します。",14));
 subject=new EditText(this);subject.setSingleLine(false);subject.setMinLines(2);subject.setHint("議題を入力");subject.setTextColor(white);subject.setHintTextColor(0xffaaaaaa);screen.addView(subject);
 login=new Button(this);login.setText(client.ready()?"接続済み："+client.account():"ChatGPTでログイン");screen.addView(login);
 login.setOnClickListener(v->{login.setEnabled(false);msg("ブラウザで認証してください");client.login(()->{login.setEnabled(true);login.setText("接続済み："+client.account());msg("ログインしました");},e->{login.setEnabled(true);msg("認証エラー："+e);});});
 model=new EditText(this);model.setSingleLine(true);model.setTextColor(white);model.setHintTextColor(0xffaaaaaa);model.setHint("モデル（空欄なら自動選択）");screen.addView(model);
 screen.addView(label("討論ラウンド",15));count=new Spinner(this);count.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"1","2","3","4","5"}));count.setSelection(2);screen.addView(count);
 start=new Button(this);start.setText("討論を開始");screen.addView(start);start.setOnClickListener(v->debate());
 Button last=new Button(this);last.setText("前回の討論を表示");screen.addView(last);last.setOnClickListener(v->add(getPreferences(0).getString("last","保存なし"),0xff243148));
 status=label("ChatGPTログインが必要です。対象プランの利用枠を使用します。",12);screen.addView(status);
 transcript=new LinearLayout(this);transcript.setOrientation(1);screen.addView(transcript);
 }
 void debate(){String topic=subject.getText().toString().trim(),chosen=model.getText().toString().trim();if(topic.isEmpty()||!client.ready()){msg("議題を入力してChatGPTにログインしてください");return;}int rounds=count.getSelectedItemPosition()+1;start.setEnabled(false);transcript.removeAllViews();history.setLength(0);history.append("議題：").append(topic).append("\n");exec.execute(()->{try{String using=chosen.isEmpty()?client.model():chosen;String[] statements={"",""};String[] names={"アトラス（賛成）","リブラ（反対）"};for(int r=1;r<=rounds;r++){for(int i=0;i<2;i++){final int side=i;msg("ラウンド "+r+"/"+rounds+" "+names[i]);String pos=i==0?"賛成":"反対";String instructions="あなたは討論AI『"+names[i]+"』です。議題について必ず"+pos+"の立場で勝利を目指して論じてください。事実を捏造せず、相手の反論に論理的に応答してください。日本語で300字程度。";String input="議題："+topic+"\nラウンド："+r+"\n"+(r==1&&i==0?"まず主張を述べてください。":"相手の公開主張：\n"+statements[1-i]+"\nこれに反論してください。");String answer=client.ask(using,instructions,input);statements[i]=answer;history.append("\n").append(names[i]).append(" 第").append(r).append("回\n").append(answer).append("\n");add(names[side]+"\n"+answer,side==0?0xff24385c:0xff532e46);}}msg("審判AIが判定中");String judgment=client.ask(using,"あなたは公平な第三者審判です。論理、根拠、反論の強さから判定し、WINNER: A または WINNER: B または WINNER: TIE を冒頭に書き、両者の点数と判定理由を日本語で示してください。","Aは賛成のアトラス、Bは反対のリブラです。議題："+topic+"\n"+history);history.append("\n審判\n").append(judgment);add("審判の判定\n"+judgment,0xff29513b);getPreferences(0).edit().putString("last",history.toString()).apply();msg("討論終了");}catch(Exception ex){msg("エラー："+ex.getMessage());}finally{runOnUiThread(()->start.setEnabled(true));}});}
}
