package jp.ninamizuki.debatearena;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.security.keystore.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.math.BigInteger;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
final class SiwcClient {
 static final String AUTH="https://auth.openai.com", API="https://api.openai.com/v1";
 final Activity activity; JSONObject session;
 SiwcClient(Activity a){activity=a;try{String s=a.getPreferences(0).getString("auth","");if(!s.isEmpty())session=new JSONObject(decrypt(s));}catch(Exception e){}}
 synchronized boolean ready(){return session!=null&&session.has("access_token");}
 synchronized String account(){return ready()?session.optString("email","ChatGPT account"):"未接続";}
 static String encode(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
 static String query(Map<String,String> map)throws Exception{StringJoiner j=new StringJoiner("&");for(Map.Entry<String,String> e:map.entrySet())j.add(encode(e.getKey())+"="+encode(e.getValue()));return j.toString();}
 static JSONObject request(String url,String method,String bearer,String body,String contentType)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod(method);c.setConnectTimeout(20000);c.setReadTimeout(120000);if(bearer!=null)c.setRequestProperty("Authorization","Bearer "+bearer);
  if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type",contentType);try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}
  try{int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream b=new ByteArrayOutputStream();if(in!=null){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);}String result=b.toString("UTF-8");if(code>=400)throw new IOException("HTTP "+code+": "+result);return new JSONObject(result);}finally{c.disconnect();}
 }
 static String rand(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return b64(b);}
 static String b64(byte[] b){return Base64.encodeToString(b,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
 static byte[] unb64(String s){return Base64.decode(s,Base64.URL_SAFE|Base64.NO_WRAP);}
 synchronized void save(JSONObject s)throws Exception{activity.getPreferences(0).edit().putString("auth",encrypt(s.toString())).apply();session=s;}
 void login(Runnable success,java.util.function.Consumer<String> failure){
  new Thread(()->{try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
   server.setSoTimeout(180000);String verifier=rand()+rand(),state=rand(),nonce=rand(),redirect="http://127.0.0.1:"+server.getLocalPort()+"/auth/callback";
   String cid=session==null?"dynamic_agent_client":session.optString("client_id","dynamic_agent_client");
   Map<String,String> q=new LinkedHashMap<>();q.put("client_id",cid);q.put("response_type","code");q.put("redirect_uri",redirect);
   q.put("scope","openid profile email offline_access resource.invoke chatgpt.tokens.use.direct");q.put("resource",API);q.put("state",state);q.put("nonce",nonce);q.put("code_challenge_method","S256");q.put("code_challenge",b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))));q.put("agent_name_hint","Debate Arena");
   activity.runOnUiThread(()->activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(AUTH+"/api/accounts/authorize?"+safeQuery(q)))));
   Map<String,String> values=new HashMap<>();
   try(Socket conn=server.accept()){conn.setSoTimeout(15000);BufferedReader r=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8));String line=r.readLine();if(line==null||!line.startsWith("GET /auth/callback?"))throw new IOException("Callback failed");String path=line.split(" ")[1];String qs=path.substring(path.indexOf('?')+1);for(String part:qs.split("&")){String[] t=part.split("=",2);if(t.length==2)values.put(URLDecoder.decode(t[0],"UTF-8"),URLDecoder.decode(t[1],"UTF-8"));}while((line=r.readLine())!=null&&!line.isEmpty()){}byte[] html="<html><meta charset='utf-8'><body>ログイン処理が完了しました。アプリに戻ってください。</body></html>".getBytes(StandardCharsets.UTF_8);OutputStream out=conn.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+html.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(html);out.flush();}
   if(!state.equals(values.get("state")))throw new SecurityException("OAuth state mismatch");if(values.containsKey("error"))throw new IOException(values.get("error"));
   String actual=values.getOrDefault("client_id",cid);if("dynamic_agent_client".equals(actual))throw new IOException("Dynamic client registration was not returned");
   Map<String,String> data=new LinkedHashMap<>();data.put("grant_type","authorization_code");data.put("client_id",actual);data.put("redirect_uri",redirect);data.put("code_verifier",verifier);data.put("code",values.getOrDefault("code",""));data.put("resource",API);
   JSONObject tokens=request(AUTH+"/api/accounts/oauth/token","POST",null,query(data),"application/x-www-form-urlencoded");
   JSONObject claims=verify(tokens.getString("id_token"),actual,nonce);
   JSONObject record=new JSONObject();record.put("client_id",actual);record.put("access_token",tokens.getString("access_token"));record.put("refresh_token",tokens.optString("refresh_token"));record.put("expires",System.currentTimeMillis()+1000L*tokens.optLong("expires_in",3600));record.put("email",claims.optString("email","ChatGPT account"));record.put("scope",tokens.optString("scope"));save(record);
   activity.runOnUiThread(success);
  }catch(Exception e){activity.runOnUiThread(()->failure.accept(String.valueOf(e.getMessage())));}},"oauth").start();
 }
 static String safeQuery(Map<String,String> q){try{return query(q);}catch(Exception e){return "";}}
 static JSONObject verify(String jwt,String clientId,String nonce)throws Exception{
  String[] p=jwt.split("\\.");if(p.length!=3)throw new SecurityException("Invalid JWT");
  JSONObject head=new JSONObject(new String(unb64(p[0]),StandardCharsets.UTF_8)),claims=new JSONObject(new String(unb64(p[1]),StandardCharsets.UTF_8));
  if(!"RS256".equals(head.optString("alg")))throw new SecurityException("Unsupported algorithm");
  JSONObject config=request(AUTH+"/.well-known/openid-configuration","GET",null,null,null);String jwks=config.getString("jwks_uri");if(!jwks.startsWith(AUTH+"/"))throw new SecurityException("Invalid key host");
  JSONArray keys=request(jwks,"GET",null,null,null).getJSONArray("keys");boolean valid=false;
  for(int i=0;i<keys.length();i++){JSONObject k=keys.getJSONObject(i);if(!head.optString("kid").equals(k.optString("kid")))continue;PublicKey key=KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new BigInteger(1,unb64(k.getString("n"))),new BigInteger(1,unb64(k.getString("e")))));Signature s=Signature.getInstance("SHA256withRSA");s.initVerify(key);s.update((p[0]+"."+p[1]).getBytes(StandardCharsets.US_ASCII));valid=s.verify(unb64(p[2]));break;}
  Object audience=claims.opt("aud");boolean aud=clientId.equals(audience)||(audience instanceof JSONArray&&((JSONArray)audience).toString().contains("\""+clientId+"\""));if(!valid||!AUTH.equals(claims.optString("iss"))||!aud||!nonce.equals(claims.optString("nonce"))||claims.optLong("exp")<=System.currentTimeMillis()/1000)throw new SecurityException("ID token verification failed");return claims;
 }
 synchronized String token()throws Exception{
  if(!ready())throw new IOException("ログインが必要です");
  if(System.currentTimeMillis()>session.optLong("expires")-120000){Map<String,String> p=new LinkedHashMap<>();p.put("grant_type","refresh_token");p.put("client_id",session.getString("client_id"));p.put("refresh_token",session.getString("refresh_token"));p.put("resource",API);JSONObject t=request(AUTH+"/api/accounts/oauth/token","POST",null,query(p),"application/x-www-form-urlencoded");JSONObject s=new JSONObject(session.toString());s.put("access_token",t.getString("access_token"));s.put("refresh_token",t.optString("refresh_token",s.optString("refresh_token")));s.put("expires",System.currentTimeMillis()+t.optLong("expires_in",3600)*1000L);save(s);}
  return session.getString("access_token");
 }
 String model()throws Exception{JSONObject response=request(API+"/models","GET",token(),null,null);JSONArray models=response.optJSONArray("models");if(models==null)models=response.optJSONArray("data");if(models==null||models.length()==0)throw new IOException("No available models");return models.getJSONObject(0).optString("slug",models.getJSONObject(0).optString("id"));}
 String ask(String model,String instructions,String input)throws Exception{
  JSONObject body=new JSONObject();body.put("model",model);body.put("instructions",instructions);body.put("input",input);body.put("stream",true);body.put("store",false);
  HttpURLConnection c=(HttpURLConnection)new URL(API+"/responses").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20000);c.setReadTimeout(180000);c.setRequestProperty("Authorization","Bearer "+token());c.setRequestProperty("Content-Type","application/json");
  try{try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();if(code>=400)throw new IOException("AI HTTP "+code);StringBuilder result=new StringBuilder();boolean done=false;try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=in.readLine())!=null){if(!line.startsWith("data: "))continue;String data=line.substring(6);if("[DONE]".equals(data))break;JSONObject e;try{e=new JSONObject(data);}catch(Exception ex){continue;}if("response.output_text.delta".equals(e.optString("type")))result.append(e.optString("delta"));if("response.completed".equals(e.optString("type")))done=true;if("response.failed".equals(e.optString("type")))throw new IOException("Model response failed");}}if(!done||result.length()==0)throw new IOException("Incomplete response");return result.toString();}finally{c.disconnect();}
 }
 SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);String alias="debate_key";if(!ks.containsAlias(alias)){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes("GCM").setEncryptionPaddings("NoPadding").build());g.generateKey();}return (SecretKey)ks.getKey(alias,null);}
 String encrypt(String p)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(p.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
 String decrypt(String p)throws Exception{String[] x=p.split(":");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(x[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(x[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}
}
