import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVFormat;

import com.google.gson.Gson;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

class CSVGenerator{
	
	String authToken = "";
	
	public static final String CSV_FILE_NAME = "data.csv";
	
	public CSVGenerator(String guildRealm, String guildSlug) throws Exception {

		List<Profile> profiles = getProfiles(guildRealm, guildSlug);
		convertToCSV(profiles);
	}
	
	public void convertToCSV(List<Profile> data) throws IOException {
		System.out.println("Writing File");
	    FileWriter out = new FileWriter(CSV_FILE_NAME);
	    try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT
	      .withHeader("mythicLevel", "score", "parTimeMs", "clearTimeMs", "percentRemaining", "bonus"))) {
	        data.forEach(profile -> {
	        	try {
		        	for(Profile.Key k : profile.mythic_plus_best_runs) {
		        		printer.printRecord(k.mythic_level, k.score, k.par_time_ms, k.clear_time_ms, ((double)k.par_time_ms - (double)k.clear_time_ms)/(double)k.par_time_ms, k.score-BaseScores.get(k.mythic_level));
		        	}
		        	for(Profile.Key k : profile.mythic_plus_alternate_runs) {
		        		printer.printRecord(k.mythic_level, k.score, k.par_time_ms, k.clear_time_ms, ((double)k.par_time_ms - (double)k.clear_time_ms)/(double)k.par_time_ms, k.score-BaseScores.get(k.mythic_level));
		        	}
	        	} catch(IOException e) {
	        		e.printStackTrace();
	        	}
	        });
	    }
	    out.close();
	}
	public List<Profile> getProfiles(String guildRealm, String guildSlug) {

		String region = "us";
		String field = "mythic_plus_best_runs,mythic_plus_alternate_runs";
		Gson gson = new Gson();
		List<Guild.Member> members = new ArrayList<Guild.Member>();
		try {
			getAuthToken();
			members = getGuildNames(guildRealm, guildSlug);
			//TODO: REMOVE THIS
//			members = members.subList(0, 20);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		List<Profile> profiles = new ArrayList<Profile>();
		double progress = 0;
		int memberCount = members.size();
		System.out.println("Preparing to load (" +memberCount+") members...");
		int numCompleted = 0;
		
		//Request Time Controls
		long curTime;
		long deltaTime;
		int numRequests = 0;
		long startTime = System.currentTimeMillis();
		int actualLoaded = 0;
		int charsNotFound = 0;
		
		for(Guild.Member m : members) {
			if(numRequests < 300) {
				String responseString = getResponseForQuery("characters/profile", "region", region, "realm", m.getRealm(), "name", m.getName(), "fields", field);
				Response response = new Gson().fromJson(responseString, Response.class);
				if(response.statusCode != 200 || responseString.contains("502")) {
					charsNotFound++;
				} else {
					actualLoaded++;
					profiles.add(gson.fromJson(responseString, Profile.class));
				}
				progress = ((double)numCompleted/(double)memberCount)*100;
				numCompleted++;
				
				numRequests++;		
				
				if(numCompleted % 5 == 0) {
					System.out.printf("Loaded/Not Found:(%d/%d).........%s%%\n", actualLoaded, charsNotFound, String.format("%.2f", progress));
				}
			}
			else {
				curTime = System.currentTimeMillis();
				deltaTime = curTime-startTime;
				if(deltaTime < 60000) {
					deltaTime = 60000-deltaTime;
					double seconds = (((double)deltaTime / 1000d) / 60d); 
					System.out.println("Reached maximum requests/min. Waiting for " + String.format("%.2f", seconds) + " seconds...");
				} else {
					numRequests = 0;
					startTime = System.currentTimeMillis();
				}
			}
			
		}
		System.out.println("Finished Loading Keys");
		System.out.println("Successfully Found (" + actualLoaded + "/" + memberCount + ") members.");
		memberCount = actualLoaded;
		
		return profiles;
	}
	
	public List<Guild.Member> getGuildNames(String guildRegion, String guildSlug) throws IOException, InterruptedException {
		
		String uri = "https://us.api.blizzard.com/data/wow/guild/"+guildRegion+"/"+guildSlug+"/roster?namespace=profile-us&locale=en_US&access_token=USRAaGWVyoVzhS3iRXT1F8HKQ1XSYeXKKp";

		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + authToken)
        .uri(URI.create(uri))
        .GET()
        .build();
		String res = client.send(request, BodyHandlers.ofString()).body();
		Gson gson = new Gson();
		List<Guild.Member> members = gson.fromJson(res, Guild.class).getMembers();
		return members;
	}
	
	public void getAuthToken() throws IOException, InterruptedException {

		String authUri = "https://us.battle.net/oauth/token?grant_type=client_credentials";
		HttpClient client = HttpClient.newHttpClient();
		String username = "7d06ac10b32343bc9e494dd9fef043cb";
		String password = "pyuitmsMxL53705E5z4FmSIZ3QYQ8uvd";
		String auth = username + ":" + password;
		
		String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
		
		HttpRequest request = HttpRequest.newBuilder()
        .header("Content-Type", "application/json")
        .header("Authorization", "Basic " + encodedAuth)
        .uri(URI.create(authUri))
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
		String res = client.send(request, BodyHandlers.ofString()).body();

		Gson gson = new Gson();
		authToken = gson.fromJson(res, AuthToken.class).access_token;
	}
	
	public String getResponseForQuery(String method, String... params){
		String host = "https://raider.io/api/v1/" + method;
		
		HttpClient client = HttpClient.newHttpClient();
		Map<String, String> parameters = new HashMap<String, String>();
		
		for(int i = 0; i < params.length; i++) {
			parameters.put(params[i], params[i+1]);
			i++;
		}

		
		try {
			URI uri = new URI(host+getParamsString(parameters));

			HttpRequest request = HttpRequest.newBuilder()
					.uri(uri)
					.build();
			
			HttpResponse<String> ret = client.send(request, BodyHandlers.ofString());
			return ret.body();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch(URISyntaxException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return "";
	}
	
	

	public String getParamsString(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        
        result.append("?");

        for (Map.Entry<String, String> entry : params.entrySet()) {
          result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
          result.append("=");
          result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
          result.append("&");
        }

        String resultString = result.toString();
        return resultString.length() > 0
          ? resultString.substring(0, resultString.length() - 1)
          : resultString;
	}
	public static void main(String[] args) throws Exception {
		new CSVGenerator(args[0], args[1]);
	}
}
