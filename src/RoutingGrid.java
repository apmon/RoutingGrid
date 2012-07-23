import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Vector;

import org.json.JSONObject;


public class RoutingGrid {
	
	private class TimeDistance {
		double distance;
		int time;
		
		public TimeDistance() {
			distance = 0.0;
			time = 0;
		}
		
		public void addTimeDistance(TimeDistance other) {
			time += other.time;
			distance += other.distance;
		}
		
		public String timeToString() {
			return String.format("%d:%02d:%02d", (time / 3600), (time / 60 % 60), time % 60);
		}
	}
	private class City {
		double lat, lon;
		String name;
	}
	
	static RoutingGrid rg;
	
	private Vector<City> cities;
	private TimeDistance[] references;
	
	private String outFileName;
	private String referenceFileName;
	
	/**
	 * @param args
	 */
	public RoutingGrid(String fileName) {
		cities = new Vector<RoutingGrid.City>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			String nextLine = br.readLine();
			while (nextLine != null) {
				String [] parts = nextLine.split("\t");
				//System.out.println(nextLine);
				if (parts.length != 3) {
					System.out.println("Line: " + nextLine + " does not have the correct number of components");
					System.exit(2);
				}
				City c = new City();
				c.lat = Double.parseDouble(parts[0]);
				c.lon = Double.parseDouble(parts[1]);
				c.name = parts[2];
				cities.add(c);
				nextLine = br.readLine();
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		System.out.println("There are " + cities.size() + " cities in the list");
	}
	
	private int citiesHash() {
		String tmp = "";
		for (City c : cities) {
			tmp += c.lat + " " + c.lon + " " + c.name;
		}
		return tmp.hashCode();
	}
	
	private TimeDistance getDistanceGoogle(City a, City b) throws IOException {
		System.out.println("Googling distance between " + a.name + " and " + b.name);
		
		String url = "http://maps.googleapis.com/maps/api/directions/json?" +
				"origin=" + a.lat + "," + a.lon + "&" +
				"destination=" + b.lat + "," + b.lon + "&" +
				"sensor=false&units=metric";

		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestProperty("User-Agent", "Apmon's City routing grid2");
		BufferedReader br = new BufferedReader(
				new InputStreamReader(
						new BufferedInputStream(conn.getInputStream())));
		try {
			String line = br.readLine();
			String jsonString = "";
			while (line != null) {
				jsonString += line;
				line = br.readLine();
			}

			JSONObject jsonObj = new JSONObject(jsonString);
			TimeDistance result = new TimeDistance();
			result.distance = jsonObj.getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONObject("distance").getInt("value");
			result.time = jsonObj.getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONObject("duration").getInt("value");
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private TimeDistance getDistance(City a, City b) throws IOException {
		System.out.println("Calculating distance between " + a.name + " and " + b.name);
		String url = "http://router.project-osrm.org/" +
				"viaroute?loc=" + a.lat + "," + a.lon + "&" +
				"loc=" + b.lat + "," + b.lon + "&" +
				"instructions=false&geometry=false";
		System.out.println(url);
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestProperty("User-Agent", "Apmon's City routing grid2");
		BufferedReader br = new BufferedReader(
				new InputStreamReader(
						new BufferedInputStream(conn.getInputStream())));
		try {
			JSONObject jsonObj = new JSONObject(br.readLine());
			TimeDistance result = new TimeDistance();
			result.distance = jsonObj.getJSONObject("route_summary").getDouble("total_distance");
			result.time = jsonObj.getJSONObject("route_summary").getInt("total_time");
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public void generateReference() {
		references = new TimeDistance[cities.size()*cities.size() - cities.size()];
		int counter = 0;
		for (City a: cities) {
			for (City b : cities) {
				if (a != b) {
					try {
						TimeDistance result = getDistanceGoogle(a, b);
						references[counter++] = result;
						try {
							Thread.sleep(2000);
						} catch (InterruptedException ie) {
							
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	private void storeReference() {
		
		try {
			File refFile = new File(referenceFileName);
			if (!refFile.exists()) refFile.createNewFile();
			System.out.println("Storing reference file: " + refFile.getAbsolutePath());
			BufferedWriter bw = new BufferedWriter(new FileWriter(refFile));
			bw.write(citiesHash() + "\n");
			for (TimeDistance td : references) {
				if (td == null) {
					bw.write(-1 + "\t" + -1 + "\n");
				} else {
					bw.write(td.time + "\t" + td.distance + "\n");
				}
			}
			bw.flush();
			bw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		
	}
	
	private void loadReference() {
		references = new TimeDistance[cities.size()*cities.size() - cities.size()];
		File refFile = new File(referenceFileName);
		System.out.println("Loading reference file: " + refFile.getAbsolutePath());
		try {
			BufferedReader br = new BufferedReader(new FileReader(refFile));
			if (Integer.parseInt(br.readLine()) != citiesHash()) {
				System.out.println("reference list does not match the current cities list");
				System.exit(4);
			}
			for (int i = 0; i < (cities.size() * cities.size() - cities.size()); i++) {
				String line = br.readLine();
				String parts[] = line.split("\t");
				TimeDistance td = new TimeDistance();
				td.time = Integer.parseInt(parts[0]);
				td.distance = Double.parseDouble(parts[1]);
				references[i] = td;
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void generateHTML() {
		try {
			TimeDistance total = new TimeDistance();
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFileName)));
			
			bw.write("<HTML><BODY><H1>Cities routing grid</H1>");
			bw.write("<table border=\"1\">");
			bw.write("<tr><th></th>");
			for (City a : cities) {
				bw.write("<th>" + a.name + "</th>");
			}
			bw.write("<th>Total</th>");
			bw.write("</tr>");
			int counter = 0;
			for (City a : cities) {
				TimeDistance rowTotal = new TimeDistance();
				bw.write("<tr align=\"center\">");
				bw.write("<th>" + a.name + "</th>");
				for (City b : cities) {
					if (a != b) {
						TimeDistance result = getDistance(a, b);
						total.addTimeDistance(result);
						rowTotal.addTimeDistance(result);
						if ((result.distance == 0) || (result.time > 3600000)) {
							bw.write("<td bgcolor=\"red\"> ");
						} else 	if ((result.distance - references[counter].distance - 50000 > 0) ||
								(result.distance - references[counter].distance > 0.05*references[counter].distance) ||
								(result.time - references[counter].time - 1800 > 0) ||
										(result.time - references[counter].time > 0.05*references[counter].time)) {
							bw.write("<td bgcolor=\"orange\"> ");
						} else {
							bw.write("<td bgcolor=\"lime\"> ");
						}
							String osrmURL = "http://map.project-osrm.org/?hl=en" + "&loc=" + a.lat + "," + a.lon +"&loc=" + b.lat + "," + b.lon;
							String googleURL = "https://maps.google.com/maps?saddr=" + a.lat + "+" + a.lon + "&daddr=" + b.lat + "+" + b.lon;
							bw.write("<a href=\"" + osrmURL +  "\">" + 
								(result.distance / 1000) + "km<br></a>" +
								"<a href=\"" + googleURL + "\"><i><small>(" + (references[counter].distance / 1000) + "km)</small></i></a><br>" + 
								"<a href=\"" + osrmURL +"\">" + result.timeToString() + "</a><br>" +
								"<a href=\"" + googleURL + "\"><i><small>(" + references[counter].timeToString() +")</small></i>" +
								"</a></td>");
						counter++;
					} else {
						bw.write("<td></td>");
					}
				}
				bw.write("<td>" + (rowTotal.distance / 1000) + "km<br>" + rowTotal.timeToString() + "</td>");
				bw.write("</tr>");
			}
			bw.write("<tr align=\"center\"><td></td>");
			for (City a : cities) {
				bw.write("<td></td>");
			}
			bw.write("<td>" + (total.distance / 1000) + "km<br>" + total.timeToString() + "</td>");
			bw.write("</tr>");
			bw.write("</table>");
			bw.write("<H3>The routing information is kindly provided by <a href=\"http://project-osrm.org/\">Open Source Routing Machine</a></H3>");
			bw.write("<H5>Data: (c) OpenStreetMap and contributors, CC-BY-SA</H5>");
			bw.write("<H5>Reference data is provided by <a href=\"https://developers.google.com/maps/documentation/directions/\">The Google Directions API</a></H5>");
			Date d = new Date();
			bw.write("<HR><H6>This page was created on " + d + "</H6>");
			bw.write("</BODY></HTML>");
			bw.flush();
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	public static void main(String[] args) {
		
		rg = new RoutingGrid(args[0]);
		rg.outFileName = args[1];
		if (args.length > 2) {
			rg.referenceFileName = args[2];
		}
		
		if ((rg.referenceFileName == null) || (args.length > 3)) {
			rg.generateReference();
		}
		if (rg.referenceFileName != null) {
			if (args.length == 4) rg.storeReference();
			rg.loadReference();
		}
		rg.generateHTML();
	}

}
