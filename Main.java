// import required libraries

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

// Defining main class for OTT platform in CLI
public class Main {
    static List<SubscriptionPlan> plans = new ArrayList<>(); //Arraylist to store plans data
    static List<Media> media_list = new ArrayList<>(); //Arraylist to store media details
    static Map<String, Integer> word_frequency = new HashMap<>(); // Tracks word search frequency
    static Trie trie = new Trie(); // Trie data structure for word completion algorithm
    static Trie cast_trie = new Trie(); // trie for cast name suggestions

    // SubscriptionPlan class (unchanged)
    static class SubscriptionPlan {
        String name, price, resolution, devices, concurrentDevices, link, platform;
        SubscriptionPlan(String name, String price, String resolution, String devices, String concurrentDevices, String link, String platform) {
            this.name = name;
            this.price = price;
            this.resolution = resolution;
            this.devices = devices;
            this.concurrentDevices = concurrentDevices;
            this.link = link;
            this.platform = platform;
        }
        double getPrice() { return Double.parseDouble(price.replaceAll("[^0-9.]", "")); }
        String getPlatform() { return platform; }
        @Override
        public String toString() {
            return String.format("Plan: %s | Price: %s | Resolution: %s | Devices: %s | Concurrent: %s | Link: %s | Platform: %s",
                    name, price, resolution, devices, concurrentDevices, link, platform);
        }
    }

    // Updated Media class with season field
    static class Media {
        String type, name, description, genre, releaseDate, season, cast, platform, url;
        Media(String type, String name, String description, String genre, String releaseDate, String season, String cast, String platform, String url) {
            this.type = type;
            this.name = name;
            this.description = description;
            this.genre = genre;
            this.releaseDate = releaseDate;
            this.season = season != null && !season.isEmpty() ? season : "-"; // Default to "-" if not provided
            this.cast = cast;
            this.platform = platform;
            this.url = url;
        }
        @Override
        public String toString() {
            String base = String.format("%s - %s\nDescription: %s\nGenre: %s\nRelease: %s\nCast: %s\nPlatform: %s\nURL: %s",
                    type, name, description, genre, releaseDate, cast, platform, url);
            if (type.equals("TV Show") && !season.equals("-")) {
                base = String.format("%s - %s\nDescription: %s\nGenre: %s\nRelease: %s\nSeason: %s\nCast: %s\nPlatform: %s\nURL: %s",
                        type, name, description, genre, releaseDate, season, cast, platform, url);
            }
            return base;
        }
    }

    // Trie class (unchanged)
    static class Trie {
        private class TrieNode {
            Map<Character, TrieNode> children = new HashMap<>();
            List<String> names = new ArrayList<>(); // Store full names (for cast) or media items (for media)
            List<Media> mediaItems = new ArrayList<>(); // Used only for media trie
        }
        private TrieNode root = new TrieNode();

        // Insert a media item (for movie/TV show names)
        void insert(String word, Media media) {
            TrieNode current = root;
            for (char ch : word.toLowerCase().toCharArray()) {
                current = current.children.computeIfAbsent(ch, c -> new TrieNode());
            }
            current.mediaItems.add(media);
        }

        // Insert a cast name (for cast suggestions)
        void insertCast(String name) {
            TrieNode current = root;
            for (char ch : name.toLowerCase().toCharArray()) {
                current = current.children.computeIfAbsent(ch, c -> new TrieNode());
            }
            if (!current.names.contains(name)) {
                current.names.add(name);
            }
        }

        // Get suggestions for movie/TV show names
        List<String> getSuggestions(String prefix, String type) {
            TrieNode current = root;
            for (char ch : prefix.toLowerCase().toCharArray()) {
                if (!current.children.containsKey(ch)) return Collections.emptyList();
                current = current.children.get(ch);
            }
            List<String> suggestions = new ArrayList<>();
            collectAllMediaNames(current, new StringBuilder(prefix.toLowerCase()), suggestions, type);
            return suggestions;
        }

        // Collect media names for suggestions
        private void collectAllMediaNames(TrieNode node, StringBuilder prefix, List<String> suggestions, String type) {
            for (Media media : node.mediaItems) {
                if (media.type.equals(type)) {
                    suggestions.add(media.name);
                }
            }
            for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
                prefix.append(entry.getKey());
                collectAllMediaNames(entry.getValue(), prefix, suggestions, type);
                prefix.deleteCharAt(prefix.length() - 1);
            }
        }

        // Get suggestions for cast names
        List<String> getCastSuggestions(String prefix) {
            TrieNode current = root;
            for (char ch : prefix.toLowerCase().toCharArray()) {
                if (!current.children.containsKey(ch)) return Collections.emptyList();
                current = current.children.get(ch);
            }
            List<String> suggestions = new ArrayList<>();
            collectAllCastNames(current, new StringBuilder(prefix.toLowerCase()), suggestions);
            return suggestions;
        }

        // Collect cast names for suggestions
        private void collectAllCastNames(TrieNode node, StringBuilder prefix, List<String> suggestions) {
            suggestions.addAll(node.names);
            for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
                prefix.append(entry.getKey());
                collectAllCastNames(entry.getValue(), prefix, suggestions);
                prefix.deleteCharAt(prefix.length() - 1);
            }
        }
    }

    // CastIndex class (new static inner class)
    static class CastIndex {
        private static Map<String, List<Media>> castIndex = new HashMap<>();

        // Build the cast index and populate cast_trie
        static void build() {
            castIndex.clear(); // Clear existing entries to prevent duplicates
            Set<String> uniqueCastNames = new HashSet<>(); // To avoid duplicate cast names in trie
            for (Media media : media_list) {
                String cast = media.cast;
                for (String actor : cast.split(",")) {
                    // Normalize actor name for indexing
                    String normalizedActor = actor.trim()
                                                 .replaceAll("^\"|\"$", "")
                                                 .replaceAll("\\p{Zs}+", " ")
                                                 .replaceAll("[^\\p{ASCII}]", "")
                                                 .toLowerCase();
                    if (normalizedActor.isEmpty()) continue;
                    // Add to castIndex
                    castIndex.putIfAbsent(normalizedActor, new ArrayList<>());
                    castIndex.get(normalizedActor).add(media);
                    // Add original actor name to cast_trie (before normalization)
                    String originalActor = actor.trim();
                    if (!originalActor.isEmpty() && !uniqueCastNames.contains(originalActor)) {
                        cast_trie.insertCast(originalActor);
                        uniqueCastNames.add(originalActor);
                    }
                }
            }
        }

        // Search for media items by actor name
        static List<Media> search(String actor) {
            // Normalize the search query to match the index
            actor = actor.trim()
                         .replaceAll("^\"|\"$", "")
                         .replaceAll("\\p{Zs}+", " ")
                         .replaceAll("[^\\p{ASCII}]", "")
                         .toLowerCase();
            return castIndex.getOrDefault(actor, new ArrayList<>());
        }
    }

    public static void main(String[] args) {
        // Load data and build indexes
        loadSubscriptionPlans("subscription_plans.csv");
        loadMediaData(new String[]{"Netflix_Data.csv", "AmazonPrime_Data.csv", "AppleTV_Data.csv"});
        CastIndex.build();

        // Load existing search frequencies from CSV (if the file exists)
        loadSearchFrequencyFromCSV();

        Scanner scanner = new Scanner(System.in);
        int choice;
        do {
            System.out.println("\n=== OTT Platform CLI ===");
            System.out.println("1. Subscription Details");
            System.out.println("2. Movies");
            System.out.println("3. TV Shows");
            System.out.println("4. Get More Information");
            System.out.println("5. Exit");
            System.out.print("Enter your choice: ");

            try {
                choice = scanner.nextInt();
                scanner.nextLine(); // Clear the newline
            } catch (InputMismatchException e) {
                System.out.println("Invalid input. Please enter a number between 1 and 5.");
                scanner.nextLine(); // Clear the invalid input
                choice = -1; // Set to invalid choice to loop back
                continue; // Skip the rest of the loop and prompt again
            }

            switch (choice) {
                case 1: showSubscriptionMenu(scanner); break;
                case 2: showMediaMenu(scanner, "Movie"); break;
                case 3: showMediaMenu(scanner, "TV Show"); break;
                case 4: showMoreInformationMenu(scanner); break;
                case 5: System.out.println("Exiting program..."); break;
                default:
                    System.out.println("Invalid choice. Please enter a number between 1 and 5.");
            }
        } while (choice != 5);
        scanner.close();
    }

    // Load subscription plans (unchanged)
    static void loadSubscriptionPlans(String fileName) {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            br.readLine(); // Skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] details = splitCSV(line);
                if (details.length == 7) {
                    plans.add(new SubscriptionPlan(details[0], details[1], details[2], details[3], details[4], details[5], details[6]));
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading subscription file: " + e.getMessage());
        }
    }

    // Updated loadMediaData to handle season column
    static void loadMediaData(String[] files) {
        for (String file : files) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                br.readLine(); // Skip header
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = splitCSV(line);
                    if (parts.length >= 8) {
                        String season = parts.length > 5 ? parts[5] : "-";
                        Media media = new Media(parts[0], parts[1], parts[2], parts[3], parts[4], season,
                                parts.length > 6 ? parts[6] : parts[5],
                                parts.length > 7 ? parts[7] : parts[6],
                                parts.length > 8 ? parts[8] : parts[7]);
                        media_list.add(media);
                        trie.insert(media.name, media); // Insert Media object into Trie
                    }
                }
            } catch (IOException e) {
                System.out.println("Error reading " + file + ": " + e.getMessage());
            }
        }
    }

    

    // CSV split method (unchanged)
    static String[] splitCSV(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else current.append(c);
        }
        values.add(current.toString().trim());
        return values.toArray(new String[0]);
    }

    // Subscription menu (unchanged)
    static void showSubscriptionMenu(Scanner scanner) {
        int subChoice;
        do {
            System.out.println("\n=== Subscription Plans ===");
            System.out.println("1. Netflix");
            System.out.println("2. Amazon Prime");
            System.out.println("3. Apple TV+");
            System.out.println("4. Crave");
            System.out.println("5. Order Cheapest to Expensive");
            System.out.println("6. Order Expensive to Cheapest");
            System.out.println("7. Back to Main Menu");
            System.out.print("Enter your choice: ");
            // subChoice = scanner.nextInt();
            // scanner.nextLine();

            try {
                subChoice = scanner.nextInt();
                scanner.nextLine(); // Clear the newline
            } catch (InputMismatchException e) {
                System.out.println("Invalid input. Please enter a number between 1 and 7.");
                scanner.nextLine(); // Clear the invalid input
                subChoice = -1; // Set to invalid choice to loop back
                continue; // Skip the rest of the loop and prompt again
            }

            switch (subChoice) {
                case 1: showPlatformDetails("Netflix"); break;
                case 2: showPlatformDetails("Amazon Prime Video"); break;
                case 3: showPlatformDetails("Apple TV+"); break;
                case 4: showPlatformDetails("Crave"); break;
                case 5: quickSort(plans, 0, plans.size() - 1, true); displaySortedPlans(); break; // quick sort used for subscription plans
                case 6: quickSort(plans, 0, plans.size() - 1, false); displaySortedPlans(); break;
                case 7: break;
                default: System.out.println("Invalid choice. Please enter a number between 1 and 7.");
            }
        } while (subChoice != 7);
    }

    static void showPlatformDetails(String platform) {
        System.out.println("\nSubscription Plans for " + platform + ":");
        plans.stream().filter(p -> p.getPlatform().equalsIgnoreCase(platform)).forEach(System.out::println);
    }

    static void quickSort(List<SubscriptionPlan> list, int low, int high, boolean ascending) {
        if (low < high) {
            int pi = partition(list, low, high, ascending);
            quickSort(list, low, pi - 1, ascending);
            quickSort(list, pi + 1, high, ascending);
        }
    }

    static int partition(List<SubscriptionPlan> list, int low, int high, boolean ascending) {
        double pivot = list.get(high).getPrice();
        int i = low - 1;
        for (int j = low; j < high; j++) {
            boolean condition = ascending ? (list.get(j).getPrice() < pivot) : (list.get(j).getPrice() > pivot);
            if (condition) {
                i++;
                Collections.swap(list, i, j);
            }
        }
        Collections.swap(list, i + 1, high);
        return i + 1;
    }

    static void displaySortedPlans() {
        System.out.println("\nSorted Subscription Plans by Price:");
        plans.forEach(System.out::println);
    }

    // Media menu (unchanged)
    static void showMediaMenu(Scanner scanner, String type) {
        int choice;
        do {
            System.out.println("\n=== " + type + " Menu ===");
            System.out.println("1. Search by Name"); // word completion
            System.out.println("2. Search by Genre"); //based on edit distance 
            System.out.println("3. Generic Search"); // for the record
            System.out.println("4. Show All Netflix " + type + "s"); 
            System.out.println("5. Show All Amazon Prime " + type + "s");
            System.out.println("6. Show All Apple TV+ " + type + "s");
            System.out.println("7. Show All Crave " + type + "s");
            System.out.println("8. Back to Main Menu");
            System.out.print("Enter your choice: ");

            try {
                choice = scanner.nextInt();
                scanner.nextLine();
            } catch (InputMismatchException e) {
                System.out.println("Invalid choice. Please enter a number between 1 and 8.");
                scanner.nextLine();
                choice = -1;
            }

            switch (choice) {
                case 1: searchByName(scanner, type); break;
                case 2: searchByGenre(scanner, type); break;
                case 3: searchByWord(scanner, type); break;
                case 4: showPlatformMedia("Netflix", type); break;
                case 5: showPlatformMedia("Amazon Prime Video", type); break;
                case 6: showPlatformMedia("Apple TV+", type); break;
                case 7: showPlatformMedia("Crave", type); break;
                case 8: break;
                default: 
                    if (choice != -1) {
                        System.out.println("Invalid choice. Please enter a number between 1 and 8.");
                    }
            }
        } while (choice != 8);
    }

    // Updated searchByName to ensure season details display for TV shows
    static void searchByName(Scanner scanner, String type) {
        System.out.print("Enter " + type + " name prefix: ");
        String prefix = scanner.nextLine();
        List<String> suggestions = trie.getSuggestions(prefix, type); // Filter by type
        if (suggestions.isEmpty()) {
            System.out.println("No suggestions found.");
            return;
        }
        System.out.println("\nSuggestions:");
        suggestions.forEach(s -> System.out.println("- " + s));
        System.out.print("Select a name to view details (or press Enter to skip): ");
        String selected = scanner.nextLine();
        if (!selected.isEmpty()) {
            media_list.stream()
                    .filter(m -> m.type.equals(type) && m.name.equalsIgnoreCase(selected))
                    .forEach(m -> System.out.println(m + "\n------------------------"));
        }
    }

    // Search by genre (unchanged)
    static void searchByGenre(Scanner scanner, String type) {
        System.out.println("Available Genres: Comedy, Thriller, Animation, Action, Drama, Horror, Adventure, Sci-fi, Sports, Documentary, Others");
        System.out.print("Enter genre: ");
        String genre = scanner.nextLine();
        String matchedGenre = findClosestGenre(genre);
        if (!isValidGenre(genre)) {
            System.out.print("Did you mean " + matchedGenre + "? (y/n): ");
            if (!scanner.nextLine().equalsIgnoreCase("y")) return;
            genre = matchedGenre;
        }
        final String finalGenre = genre;
        media_list.stream()
                .filter(m -> m.type.equals(type) && mapToValidGenre(m.genre).equalsIgnoreCase(finalGenre))
                .forEach(m -> System.out.println(m + "\n------------------------"));
    }

    static boolean isValidGenre(String genre) {
        String[] VALID_GENRES = {"Comedy", "Thriller", "Animation", "Action", "Drama", "Horror", "Adventure", "Sci-fi", "Sports", "Documentary"};
        return Arrays.stream(VALID_GENRES).anyMatch(g -> g.equalsIgnoreCase(genre));
    }

    static String findClosestGenre(String input) {
        String[] VALID_GENRES = {"Comedy", "Thriller", "Animation", "Action", "Drama", "Horror", "Adventure", "Sci-fi", "Sports", "Documentary"};
        int minDistance = Integer.MAX_VALUE;
        String closest = VALID_GENRES[0];
        for (String genre : VALID_GENRES) {
            int distance = levenshteinDistance(input.toLowerCase(), genre.toLowerCase());
            if (distance < minDistance) {
                minDistance = distance;
                closest = genre;
            }
        }
        return closest;
    }

    static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) dp[i][j] = j;
                else if (j == 0) dp[i][j] = i;
                else dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1));
            }
        }
        return dp[s1.length()][s2.length()];
    }

    static String mapToValidGenre(String genre) {
        String[] VALID_GENRES = {"Comedy", "Thriller", "Animation", "Action", "Drama", "Horror", "Adventure", "Sci-fi", "Sports", "Documentary"};
        return Arrays.stream(VALID_GENRES).anyMatch(g -> g.equalsIgnoreCase(genre)) ? genre : "Others";
    }

    


    // Updated searchByWord to save frequency to CSV after each search
    static void searchByWord(Scanner scanner, String type) {
        System.out.print("Enter word to search in " + type + " name/description: ");
        String word = scanner.nextLine().toLowerCase().trim();
        word_frequency.put(word, word_frequency.getOrDefault(word, 0) + 1);

        // Save the updated frequency to CSV
        saveSearchFrequencyToCSV();

        String regex = "\\b" + Pattern.quote(word) + "(s)?\\b";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);

        List<Map.Entry<Media, Integer>> resultsWithFrequency = new ArrayList<>();

        for (Media m : media_list) {
            if (!m.type.equals(type)) continue;

            Matcher nameMatcher = pattern.matcher(m.name);
            Matcher descMatcher = pattern.matcher(m.description);

            if (nameMatcher.find() || descMatcher.find()) {
                int frequency = 0;
                descMatcher.reset();
                while (descMatcher.find()) {
                    frequency++;
                }
                resultsWithFrequency.add(new AbstractMap.SimpleEntry<>(m, frequency));
            }
        }

        resultsWithFrequency.sort((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()));

        if (resultsWithFrequency.isEmpty()) {
            System.out.println("No " + type + "s found with the word '" + word + "' or '" + word + "s'.");
        } else {
            System.out.println("\nResults (ranked by frequency in description):");
            for (Map.Entry<Media, Integer> entry : resultsWithFrequency) {
                Media m = entry.getKey();
                int freq = entry.getValue();
                System.out.println(m + "\n[Word '" + word + "' appears " + freq + " time(s) in description]");
                System.out.println("------------------------");
            }
        }

        System.out.println("Search frequency for '" + word + "': " + word_frequency.get(word));
    }


    // Show all media for a platform (unchanged)
    static void showPlatformMedia(String platform, String type) {
        System.out.println("\nAll " + type + "s on " + platform + ":");
        media_list.stream()
                .filter(m -> m.type.equals(type) && m.platform.equalsIgnoreCase(platform))
                .forEach(m -> System.out.println(m + "\n------------------------"));
    }

    // More Information menu (updated to use CastIndex)
    static void showMoreInformationMenu(Scanner scanner) {
        int choice;
        do {
            System.out.println("\n=== More Information Menu ===");
            System.out.println("1. Search by Cast");
            System.out.println("2. Get Contact Details");
            System.out.println("3. Back to Main Menu");
            System.out.print("Enter your choice: ");

            try {
                choice = scanner.nextInt();
                scanner.nextLine();
            } catch (InputMismatchException e) {
                System.out.println("Invalid choice. Please enter a number between 1 and 3.");
                scanner.nextLine();
                choice = -1;
            }

            switch (choice) {
                case 1: searchByCast(scanner); break;
                case 2: getContactDetails(scanner); break;
                case 3: System.out.println("Returning to Main Menu..."); break;
                default:
                    if (choice != -1) {
                        System.out.println("Invalid choice. Please enter a number between 1 and 3.");
                    }
            }
        } while (choice != 3);
    }


    // Get Contact Details (unchanged)
    static void getContactDetails(Scanner scanner) {
        System.out.println("\n=== Get Contact Details ===");
        System.out.println("Select a platform:");
        System.out.println("1. Netflix");
        System.out.println("2. Amazon Prime");
        System.out.println("3. Apple TV+");
        System.out.println("4. Crave");
        System.out.print("Enter your choice: ");

        int platformChoice;
        try {
            platformChoice = scanner.nextInt();
            scanner.nextLine();
        } catch (InputMismatchException e) {
            System.out.println("Invalid choice. Please enter a number between 1 and 4.");
            scanner.nextLine();
            return;
        }

        String platform;
        String parserPlatform;
        switch (platformChoice) {
            case 1:
                platform = "Netflix";
                parserPlatform = "netflix";
                break;
            case 2:
                platform = "Amazon Prime Video";
                parserPlatform = "amazon-prime";
                break;
            case 3:
                platform = "Apple TV+";
                parserPlatform = "apple-tv";
                break;
            case 4:
                platform = "Crave";
                parserPlatform = "crave";
                break;
            default:
                System.out.println("Invalid choice. Please enter a number between 1 and 4.");
                return;
        }

        try {
            String contactDetails = fetchContactDetails(parserPlatform);
            System.out.println("\nContact Details for " + platform + ":");
            System.out.println(contactDetails);
        } catch (Exception e) {
            System.out.println("Error fetching contact details for " + platform + ": " + e.getMessage());
        }
    }


    // Updated fetchContactDetails to use websiteParse
    static String fetchContactDetails(String platform) throws Exception {
        return websiteParse(platform);
    }


    // Modified websiteParse to return a formatted string
    static String websiteParse(String platform) throws Exception {
        String url = "";
        switch (platform) {
            case "netflix":
                url = "https://www.netflix.com";
                break;
            case "crave":
                url = "https://www.crave.ca/en/contact";
                break;
            case "amazon-prime":
                url = "https://www.amazon.ca/gp/help/customer/display.html?nodeId=508510";
                break;
            case "apple-tv":
                url = "https://www.apple.com/ca/apple-tv-plus/";
                break;
            default:
                throw new Exception("Unsupported platform: " + platform);
        }

        try {
            // Fetch and parse HTML document using JSoup
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36")
                    .timeout(10000)
                    .get();

            String text = doc.text();
            String emailRegex = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}";
            Pattern emailPattern = Pattern.compile(emailRegex);
            Matcher emailMatcher = emailPattern.matcher(text);
            String phoneRegex = "";
            if (platform.equals("apple-tv") || platform.equals("crave")) {
                phoneRegex = "1-8\\d{2}-[A-Z0-9-]{6,8}";
            } else {
                phoneRegex = "\\+?\\d{0,3}[\\s-]?(\\(?\\d{3}\\)?[\\s-]?\\d{3}[\\s-]?\\d{4})";
            }

            Pattern phonePattern = Pattern.compile(phoneRegex);
            Matcher phoneMatcher = phonePattern.matcher(text);

            StringBuilder contactDetails = new StringBuilder();

            // Collect email addresses
            List<String> emails = new ArrayList<>();
            while (emailMatcher.find()) {
                emails.add(emailMatcher.group());
            }
            if (emails.isEmpty()) {
                contactDetails.append("Email: Not found\n");
            } else {
                contactDetails.append("Email(s):\n");
                for (String email : emails) {
                    contactDetails.append("  - ").append(email).append("\n");
                }
            }

            // Collect phone numbers
            List<String> phones = new ArrayList<>();
            while (phoneMatcher.find()) {
                phones.add(phoneMatcher.group());
            }
            if (phones.isEmpty()) {
                contactDetails.append("Phone: Not found\n");
            } else {
                contactDetails.append("Phone Number(s):\n");
                for (String phone : phones) {
                    contactDetails.append("  - ").append(phone).append("\n");
                }
            }

            // Add website URL
            if (!emails.isEmpty() || !phones.isEmpty()) {
                contactDetails.append("Website: ").append(url);
            }

            return contactDetails.toString();
        } catch (IOException e) {
            throw new Exception("Failed to fetch contact details due to a network or parsing error: " + e.getMessage());
        }
    }


    //Search by Cast
    // static void searchByCast(Scanner scanner) {
    //     System.out.print("Enter actor's name: ");
    //     String actor = scanner.nextLine().trim()
    //                           .replaceAll("^\"|\"$", "") // Remove surrounding quotes
    //                           .replaceAll("\\p{Zs}+", " ") // Normalize spaces
    //                           .replaceAll("[^\\p{ASCII}]", "")
    //                           .toLowerCase(); // Convert to lowercase

    //     System.out.println("DEBUG: Searching for '" + actor + "' in castIndex...");

    //     if (!castIndex.containsKey(actor)) {
    //         System.out.println("❌ No movies or TV shows found for actor: " + actor);
    //         return;
    //     }

    //     List<Media> results = castIndex.get(actor);
    //     System.out.println("\nMovies & TV Shows featuring **" + actor + "**:");
    //     for (Media m : results) {
    //         System.out.println(m + "\n------------------------");
    //     }
    // }

    // Search by Cast (updated with suggestions)
    static void searchByCast(Scanner scanner) {
        System.out.print("Enter cast name prefix"); // used trie for cast suggestions
        String prefix = scanner.nextLine();
        List<String> suggestions = cast_trie.getCastSuggestions(prefix);
        if (suggestions.isEmpty()) {
            System.out.println("No cast members found with prefix: " + prefix);
            return;
        }
        System.out.println("\nSuggestions:");
        suggestions.forEach(s -> System.out.println("- " + s));
        System.out.print("Select a cast member to view details (or press Enter to skip): ");
        String selected = scanner.nextLine();
        if (!selected.isEmpty()) {
            List<Media> results = CastIndex.search(selected);
            if (results.isEmpty()) {
                System.out.println("No movies or TV shows found for cast member: " + selected);
            } else {
                System.out.println("\nMovies & TV Shows featuring " + selected + ":");
                for (Media m : results) {
                    System.out.println(m + "\n------------------------");
                }
            }
        }
    }

    // New method to load search frequencies from CSV (if it exists)
    static void loadSearchFrequencyFromCSV() {
        File file = new File("search_frequency.csv");
        if (!file.exists()) {
            return; // No file to load
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] parts = splitCSV(line);
                if (parts.length == 2) {
                    String word = parts[0];
                    int frequency = Integer.parseInt(parts[1]);
                    word_frequency.put(word, frequency);
                }
            }
        } catch (IOException e) {
            System.out.println("Error loading search frequency from CSV: " + e.getMessage());
        }
    }

    // New method to save search frequencies to CSV
    static void saveSearchFrequencyToCSV() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("search_frequency.csv"))) {
            // Write header
            bw.write("Word,Frequency");
            bw.newLine();

            // Write each word and its frequency
            for (Map.Entry<String, Integer> entry : word_frequency.entrySet()) {
                bw.write(String.format("\"%s\",%d", entry.getKey(), entry.getValue()));
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error saving search frequency to CSV: " + e.getMessage());
        }
    }

    
}