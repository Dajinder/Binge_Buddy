// Importing all the required libraries for file handling, data structures, regex, and HTML parsing
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

// Defining the main class for the OTT platform Command Line Interface (CLI) project
public class Main {
    // ArrayList to store subscription plan details loaded from CSV
    static List<subscription_plan> plans = new ArrayList<>();
    // ArrayList to store media details (movies and TV shows) loaded from CSV files
    static List<Media> media_list = new ArrayList<>();
    // HashMap to track the frequency of words searched by the user
    static Map<String, Integer> word_frequency = new HashMap<>();
    // Trie data structure for efficient prefix-based search of media names
    static Trie trie = new Trie();
    // Trie data structure specifically for suggesting cast names based on user input
    static Trie cast_trie = new Trie();

    // Class to encapsulate subscription plan details fetched from a CSV file
    static class subscription_plan {
        // Instance variables to hold plan attributes
        String name, price, resolution, devices, concurrent_devices, link, platform;

        // Constructor to initialize a subscription plan with provided details
        subscription_plan(String name, String price, String resolution, String devices, String concurrent_devices, String link, String platform) {
            this.name = name;
            this.price = price;
            this.resolution = resolution;
            this.devices = devices;
            this.concurrent_devices = concurrent_devices;
            this.link = link;
            this.platform = platform;
        }

        // Method to extract and return the numeric price value from the price string
        double get_price() { 
            return Double.parseDouble(price.replaceAll("[^0-9.]", "")); 
        }

        // Method to return the platform name associated with this plan
        String get_platform() { 
            return platform; 
        }

        // Override toString to provide a formatted string representation of the subscription plan
        @Override
        public String toString() {
            return String.format("Plan: %s | Price: %s | Resolution: %s | Devices: %s | Concurrent: %s | Link: %s | Platform: %s",
                    name, price, resolution, devices, concurrent_devices, link, platform);
        }
    }

    // Class to represent media items (movies or TV shows) with details from CSV
    static class Media {
        // Instance variables for media attributes
        String type, name, description, genre, releaseDate, season, cast, platform, url;

        // Constructor to initialize a media object with provided details
        Media(String type, String name, String description, String genre, String releaseDate, String season, String cast, String platform, String url) {
            this.type = type;
            this.name = name;
            this.description = description;
            this.genre = genre;
            this.releaseDate = releaseDate;
            // Set season to "-" if null or empty, otherwise use provided value
            this.season = season != null && !season.isEmpty() ? season : "-";
            this.cast = cast;
            this.platform = platform;
            this.url = url;
        }

        // Override toString to provide a formatted string representation of the media item
        @Override
        public String toString() {
            String base = String.format("%s - %s\nDescription: %s\nGenre: %s\nRelease: %s\nCast: %s\nPlatform: %s\nURL: %s",
                    type, name, description, genre, releaseDate, cast, platform, url);
            // Include season in output only for TV shows and if season is specified
            if (type.equals("TV Show") && !season.equals("-")) {
                base = String.format("%s - %s\nDescription: %s\nGenre: %s\nRelease: %s\nSeason: %s\nCast: %s\nPlatform: %s\nURL: %s",
                        type, name, description, genre, releaseDate, season, cast, platform, url);
            }
            return base;
        }
    }

    // Trie class for efficient prefix-based searching and autocomplete functionality
    static class Trie {
        // Nested class representing a node in the Trie
        private class TrieNode {
            // Map to store child nodes with characters as keys
            Map<Character, TrieNode> child_node = new HashMap<>();
            // List to store full names (used for cast suggestions)
            List<String> names = new ArrayList<>();
            // List to store media items (used for media name suggestions)
            List<Media> media_item = new ArrayList<>();
        }
        // Root node of the Trie
        private TrieNode root = new TrieNode();

        // Method to insert a media item into the Trie using its name
        void insert(String word, Media media) {
            TrieNode current = root;
            // Iterate through each character in the lowercase word
            for (char ch : word.toLowerCase().toCharArray()) {
                // Create a new node if the character doesn't exist
                current = current.child_node.computeIfAbsent(ch, c -> new TrieNode());
            }
            // Add the media item to the final node's media list
            current.media_item.add(media);
        }

        // Method to insert a cast name into the Trie for suggestions
        void insert_cast_name(String name) {
            TrieNode current = root;
            // Iterate through each character in the lowercase name
            for (char ch : name.toLowerCase().toCharArray()) {
                current = current.child_node.computeIfAbsent(ch, c -> new TrieNode());
            }
            // Add the name to the node's list if not already present
            if (!current.names.contains(name)) {
                current.names.add(name);
            }
        }

        // Method to retrieve suggestions based on a prefix and media type
        List<String> get_suggestions(String prefix, String type) {
            TrieNode current = root;
            // Traverse the Trie to the node matching the prefix
            for (char ch : prefix.toLowerCase().toCharArray()) {
                if (!current.child_node.containsKey(ch)) return Collections.emptyList();
                current = current.child_node.get(ch);
            }
            // List to store suggestions
            List<String> suggestions = new ArrayList<>();
            // Fetch all media names starting from this node
            fetch_all_media_name(current, new StringBuilder(prefix.toLowerCase()), suggestions, type);
            return suggestions;
        }

        // Helper method to recursively fetch all media names from a Trie node
        private void fetch_all_media_name(TrieNode node, StringBuilder prefix, List<String> suggestions, String type) {
            // Add media names of the specified type to suggestions
            for (Media media : node.media_item) {
                if (media.type.equals(type)) {
                    suggestions.add(media.name);
                }
            }
            // Recursively explore all child nodes
            for (Map.Entry<Character, TrieNode> entry : node.child_node.entrySet()) {
                prefix.append(entry.getKey());
                fetch_all_media_name(entry.getValue(), prefix, suggestions, type);
                prefix.deleteCharAt(prefix.length() - 1); // Backtrack
            }
        }

        // Method to fetch cast name suggestions based on a prefix
        List<String> fetch_cast_suggestions(String prefix) {
            TrieNode current = root;
            // Traverse the Trie to the node matching the prefix
            for (char ch : prefix.toLowerCase().toCharArray()) {
                if (!current.child_node.containsKey(ch)) return Collections.emptyList();
                current = current.child_node.get(ch);
            }
            List<String> suggestions = new ArrayList<>();
            // Fetch all cast names starting from this node
            fetch_all_cast_name(current, new StringBuilder(prefix.toLowerCase()), suggestions);
            return suggestions;
        }

        // Helper method to recursively fetch all cast names from a Trie node
        private void fetch_all_cast_name(TrieNode node, StringBuilder prefix, List<String> suggestions) {
            suggestions.addAll(node.names); // Add all names stored at this node
            // Recursively explore all child nodes
            for (Map.Entry<Character, TrieNode> entry : node.child_node.entrySet()) {
                prefix.append(entry.getKey());
                fetch_all_cast_name(entry.getValue(), prefix, suggestions);
                prefix.deleteCharAt(prefix.length() - 1); // Backtrack
            }
        }
    }

    // Class to manage an index of cast members and their associated media
    static class cast_index {
        // Map to store actors and the list of media they appear in
        private static Map<String, List<Media>> cast_index = new HashMap<>();

        // Method to build the cast index and populate the cast Trie
        static void build() {
            cast_index.clear(); // Clear existing entries to avoid duplicates
            Set<String> cast_name_uniq = new HashSet<>(); // Set to ensure unique cast names in Trie
            for (Media media : media_list) {
                String cast = media.cast;
                // Split cast string into individual actors
                for (String actor : cast.split(",")) {
                    // Normalize actor name for consistent indexing
                    String actor_norm = actor.trim()
                            .replaceAll("^\"|\"$", "") // Remove leading/trailing quotes
                            .replaceAll("\\p{Zs}+", " ") // Replace multiple spaces with single space
                            .replaceAll("[^\\p{ASCII}]", "") // Remove non-ASCII characters
                            .toLowerCase();
                    if (actor_norm.isEmpty()) continue; // Skip empty entries
                    // Add to cast index if not already present
                    cast_index.putIfAbsent(actor_norm, new ArrayList<>());
                    cast_index.get(actor_norm).add(media);
                    // Add original actor name to cast Trie for suggestions
                    String actor_original = actor.trim();
                    if (!actor_original.isEmpty() && !cast_name_uniq.contains(actor_original)) {
                        cast_trie.insert_cast_name(actor_original);
                        cast_name_uniq.add(actor_original);
                    }
                }
            }
        }

        // Method to search for media items by actor name
        static List<Media> search(String actor) {
            // Normalize the search query to match indexed format
            actor = actor.trim()
                    .replaceAll("^\"|\"$", "")
                    .replaceAll("\\p{Zs}+", " ")
                    .replaceAll("[^\\p{ASCII}]", "")
                    .toLowerCase();
            // Return list of media for the actor, or empty list if not found
            return cast_index.getOrDefault(actor, new ArrayList<>());
        }
    }

    // Main method to run the CLI application
    public static void main(String[] args) {
        // Load initial data from CSV files and build indexes
        load_subs_plans("subscription_plans.csv");
        load_media_data(new String[]{"Netflix_Data.csv", "AmazonPrime_Data.csv", "AppleTV_Data.csv", "Crave_Data.csv"});
        cast_index.build();
        load_search_freq_csv(); // Load previous search frequencies

        Scanner scanner = new Scanner(System.in); // Scanner for user input
        int choice;
        do {
            // Display main menu options
            System.out.println("\n=== OTT Platform CLI ===");
            System.out.println("1. Subscription Details");
            System.out.println("2. Movies");
            System.out.println("3. TV Shows");
            System.out.println("4. Get More Information");
            System.out.println("5. Exit");
            System.out.print("Enter your choice: ");

            try {
                choice = scanner.nextInt();
                scanner.nextLine(); // Consume newline character
            } catch (InputMismatchException e) {
                // Handle non-integer input gracefully
                System.out.println("Invalid input. Please enter a number between 1 and 5.");
                scanner.nextLine(); // Clear invalid input
                choice = -1; // Reset choice to loop back
                continue;
            }

            // Process user's menu selection
            switch (choice) {
                case 1: show_subs_menu(scanner); break;
                case 2: show_media_menu(scanner, "Movie"); break;
                case 3: show_media_menu(scanner, "TV Show"); break;
                case 4: show_more_info_menu(scanner); break;
                case 5: System.out.println("Exiting program..."); break;
                default:
                    System.out.println("Invalid choice. Please enter a number between 1 and 5.");
            }
        } while (choice != 5);
        scanner.close(); // Close scanner resource
    }

    // Method to load search frequency data from a CSV file
    static void load_search_freq_csv() {
        File file = new File("search_frequency.csv");
        if (!file.exists()) {
            return; // Exit if file doesn't exist
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            br.readLine(); // Skip header row
            while ((line = br.readLine()) != null) {
                String[] parts = split_csv(line);
                if (parts.length == 2) {
                    String word = parts[0];
                    int frequency = Integer.parseInt(parts[1]);
                    word_frequency.put(word, frequency); // Populate frequency map
                }
            }
        } catch (IOException e) {
            System.out.println("Error loading search frequency from CSV: " + e.getMessage());
        }
    }

    // Method to save search frequency data to a CSV file
    static void save_search_freq_csv() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("search_frequency.csv"))) {
            bw.write("Word,Frequency"); // Write CSV header
            bw.newLine();
            // Write each word-frequency pair
            for (Map.Entry<String, Integer> entry : word_frequency.entrySet()) {
                bw.write(String.format("\"%s\",%d", entry.getKey(), entry.getValue()));
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error saving search frequency to CSV: " + e.getMessage());
        }
    }

    // Method to load subscription plans from a CSV file
    static void load_subs_plans(String fileName) {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            br.readLine(); // Skip header row
            String line;
            while ((line = br.readLine()) != null) {
                String[] details = split_csv(line);
                if (details.length == 7) {
                    // Create and add new subscription plan object
                    plans.add(new subscription_plan(details[0], details[1], details[2], details[3], details[4], details[5], details[6]));
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading subscription file: " + e.getMessage());
        }
    }

    // Method to load media data from multiple CSV files
    static void load_media_data(String[] files) {
        for (String file : files) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                br.readLine(); // Skip header row
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = split_csv(line);
                    if (parts.length >= 8) {
                        // Handle optional season field
                        String season = parts.length > 5 ? parts[5] : "-";
                        Media media = new Media(parts[0], parts[1], parts[2], parts[3], parts[4], season,
                                parts.length > 6 ? parts[6] : parts[5],
                                parts.length > 7 ? parts[7] : parts[6],
                                parts.length > 8 ? parts[8] : parts[7]);
                        media_list.add(media);
                        trie.insert(media.name, media); // Insert into Trie for searching
                    }
                }
            } catch (IOException e) {
                System.out.println("Error reading " + file + ": " + e.getMessage());
            }
        }
    }

    // Method to split CSV lines while handling quoted fields
    static String[] split_csv(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes; // Toggle quote state
            else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim()); // Add field when comma is outside quotes
                current.setLength(0); // Reset builder
            } else {
                current.append(c); // Append character to current field
            }
        }
        values.add(current.toString().trim()); // Add last field
        return values.toArray(new String[0]);
    }

    // Method to display and handle the subscription plans menu
    static void show_subs_menu(Scanner scanner) {
        int sub_choice;
        do {
            // Display subscription menu options
            System.out.println("\n=== Subscription Plans ===");
            System.out.println("1. Netflix");
            System.out.println("2. Amazon Prime");
            System.out.println("3. Apple TV+");
            System.out.println("4. Crave");
            System.out.println("5. Order Cheapest to Expensive");
            System.out.println("6. Order Expensive to Cheapest");
            System.out.println("7. Back to Main Menu");
            System.out.print("Enter your choice: ");

            try {
                sub_choice = scanner.nextInt();
                scanner.nextLine(); // Clear newline
            } catch (InputMismatchException e) {
                System.out.println("Invalid input. Please enter a number between 1 and 7.");
                scanner.nextLine();
                sub_choice = -1;
                continue;
            }

            // Process user's subscription menu choice
            switch (sub_choice) {
                case 1: show_platform_detail("Netflix"); break;
                case 2: show_platform_detail("Amazon Prime Video"); break;
                case 3: show_platform_detail("Apple TV+"); break;
                case 4: show_platform_detail("Crave"); break;
                case 5: quick_sort(plans, 0, plans.size() - 1, true); display_plans_sorted(); break;
                case 6: quick_sort(plans, 0, plans.size() - 1, false); display_plans_sorted(); break;
                case 7: break;
                default: System.out.println("Invalid choice. Please enter a number between 1 and 7.");
            }
        } while (sub_choice != 7);
    }

    // Method to display subscription plans for a specific platform
    static void show_platform_detail(String platform) {
        System.out.println("\nSubscription Plans for " + platform + ":");
        plans.stream()
                .filter(p -> p.get_platform().equalsIgnoreCase(platform))
                .forEach(System.out::println);
    }

    // QuickSort algorithm to sort subscription plans by price
    static void quick_sort(List<subscription_plan> list, int low, int high, boolean ascending) {
        if (low < high) {
            int pi = partition(list, low, high, ascending); // Get partition index
            quick_sort(list, low, pi - 1, ascending); // Sort left partition
            quick_sort(list, pi + 1, high, ascending); // Sort right partition
        }
    }

    // Partition method for QuickSort
    static int partition(List<subscription_plan> list, int low, int high, boolean ascending) {
        double pivot = list.get(high).get_price();
        int i = low - 1;
        for (int j = low; j < high; j++) {
            // Compare based on ascending or descending order
            boolean condition = ascending ? (list.get(j).get_price() < pivot) : (list.get(j).get_price() > pivot);
            if (condition) {
                i++;
                Collections.swap(list, i, j); // Swap elements
            }
        }
        Collections.swap(list, i + 1, high); // Place pivot in correct position
        return i + 1;
    }

    // Method to display sorted subscription plans
    static void display_plans_sorted() {
        System.out.println("\nSorted Subscription Plans by Price:");
        plans.forEach(System.out::println);
    }

    // Method to display and handle the media menu (Movies or TV Shows)
    static void show_media_menu(Scanner scanner, String type) {
        int choice;
        do {
            System.out.println("\n=== " + type + " Menu ===");
            System.out.println("1. Search by Name");
            System.out.println("2. Search by Genre");
            System.out.println("3. Generic Search");
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
                case 1: search_by_name(scanner, type); break;
                case 2: search_by_genre(scanner, type); break;
                case 3: generic_word_search(scanner, type); break;
                case 4: display_platform_media("Netflix", type); break;
                case 5: display_platform_media("Amazon Prime Video", type); break;
                case 6: display_platform_media("Apple TV+", type); break;
                case 7: display_platform_media("Crave", type); break;
                case 8: break;
                default:
                    if (choice != -1) {
                        System.out.println("Invalid choice. Please enter a number between 1 and 8.");
                    }
            }
        } while (choice != 8);
    }

    // Method to search media by name with autocomplete suggestions
    static void search_by_name(Scanner scanner, String type) {
        System.out.print("Enter " + type + " name prefix: ");
        String prefix = scanner.nextLine();
        List<String> suggestions = trie.get_suggestions(prefix, type);
        if (suggestions.isEmpty()) {
            System.out.println("No suggestions found.");
            return;
        }
        System.out.println("\nSuggestions:");
        suggestions.forEach(s -> System.out.println("- " + s));
        System.out.print("Select a name to view details (or press Enter to skip): ");
        String selected = scanner.nextLine();
        if (!selected.isEmpty()) {
            // Display details of selected media item
            media_list.stream()
                    .filter(m -> m.type.equals(type) && m.name.equalsIgnoreCase(selected))
                    .forEach(m -> System.out.println(m + "\n------------------------"));
        }
    }

    // Method to search media by genre with spell-check functionality
    static void search_by_genre(Scanner scanner, String type) {
        System.out.println("Available Genres: Comedy, Thriller, Animation, Action, Drama, Horror, Adventure, Sci-fi, Sports, Documentary, Others");
        System.out.print("Enter genre: ");
        String genre = scanner.nextLine();
        String genre_match = find_nearest_genre(genre);
        if (!is_valid_genre(genre)) {
            System.out.print("Did you mean " + genre_match + "? (y/n): ");
            if (!scanner.nextLine().equalsIgnoreCase("y")) return;
            genre = genre_match;
        }
        final String final_genre = genre;
        // Filter and display media items matching the genre
        media_list.stream()
                .filter(m -> m.type.equals(type) && map_to_valid_genre(m.genre).equalsIgnoreCase(final_genre))
                .forEach(m -> System.out.println(m + "\n------------------------"));
    }

    // Method to validate if a genre is in the predefined list
    static boolean is_valid_genre(String genre) {
        String[] VALID_GENRES = {"Comedy", "Thriller", "Animation", "Action", "Drama", "Horror", "Adventure", "Sci-fi", "Sports", "Documentary"};
        return Arrays.stream(VALID_GENRES).anyMatch(g -> g.equalsIgnoreCase(genre));
    }

    // Method to find the nearest valid genre using Levenshtein distance
    static String find_nearest_genre(String input) {
        String[] VALID_GENRES = {"Comedy", "Thriller", "Animation", "Action", "Drama", "Horror", "Adventure", "Sci-fi", "Sports", "Documentary"};
        int min_dist = Integer.MAX_VALUE;
        String closest = VALID_GENRES[0];
        for (String genre : VALID_GENRES) {
            int lev_dist = lev_dist(input.toLowerCase(), genre.toLowerCase());
            if (lev_dist < min_dist) {
                min_dist = lev_dist;
                closest = genre;
            }
        }
        return closest;
    }

    // Levenshtein distance algorithm to measure string similarity
    static int lev_dist(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) dp[i][j] = j; // Base case: empty s1
                else if (j == 0) dp[i][j] = i; // Base case: empty s2
                else dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1));
            }
        }
        return dp[s1.length()][s2.length()];
    }

    // Method to map any genre to a valid genre or "Others"
    static String map_to_valid_genre(String genre) {
        String[] VALID_GENRES = {"Comedy", "Thriller", "Animation", "Action", "Drama", "Horror", "Adventure", "Sci-fi", "Sports", "Documentary"};
        return Arrays.stream(VALID_GENRES).anyMatch(g -> g.equalsIgnoreCase(genre)) ? genre : "Others";
    }

    // Method for generic word search in media names and descriptions
    static void generic_word_search(Scanner scanner, String type) {
        System.out.print("Enter word to search in " + type + " name/description: ");
        String word = scanner.nextLine().toLowerCase().trim();
        // Update search frequency for the word
        word_frequency.put(word, word_frequency.getOrDefault(word, 0) + 1);
        save_search_freq_csv(); // Save updated frequencies

        // Regular expression to match the word (with optional 's')
        String regex = "\\b" + Pattern.quote(word) + "(s)?\\b";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        List<Map.Entry<Media, Integer>> res_with_freq = new ArrayList<>();

        for (Media m : media_list) {
            if (!m.type.equals(type)) continue;
            Matcher nameMatcher = pattern.matcher(m.name);
            Matcher descMatcher = pattern.matcher(m.description);
            if (nameMatcher.find() || descMatcher.find()) {
                int frequency = 0;
                descMatcher.reset();
                while (descMatcher.find()) {
                    frequency++; // Count occurrences in description
                }
                res_with_freq.add(new AbstractMap.SimpleEntry<>(m, frequency));
            }
        }

        // Sort results by frequency (descending order for page ranking)
        res_with_freq.sort((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()));

        if (res_with_freq.isEmpty()) {
            System.out.println("No " + type + "s found with the word '" + word + "' or '" + word + "s'.");
        } else {
            System.out.println("\nResults (ranked by frequency in description):");
            for (Map.Entry<Media, Integer> entry : res_with_freq) {
                Media m = entry.getKey();
                int freq = entry.getValue();
                System.out.println(m + "\n[Word '" + word + "' appears " + freq + " time(s) in description]");
                System.out.println("------------------------");
            }
        }
        System.out.println("Search frequency for '" + word + "': " + word_frequency.get(word));
    }

    // Method to display all media items for a specific platform and type
    static void display_platform_media(String platform, String type) {
        System.out.println("\nAll " + type + "s on " + platform + ":");
        media_list.stream()
                .filter(m -> m.type.equals(type) && m.platform.equalsIgnoreCase(platform))
                .forEach(m -> System.out.println(m + "\n------------------------"));
    }

    // Method to display and handle the "More Information" menu
    static void show_more_info_menu(Scanner scanner) {
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
                case 1: search_by_cast(scanner); break;
                case 2: get_contact_info(scanner); break;
                case 3: System.out.println("Returning to Main Menu..."); break;
                default:
                    if (choice != -1) {
                        System.out.println("Invalid choice. Please enter a number between 1 and 3.");
                    }
            }
        } while (choice != 3);
    }

    // Method to retrieve and display contact information for a platform
    static void get_contact_info(Scanner scanner) {
        System.out.println("\n=== Get Contact Details ===");
        System.out.println("Select a platform:");
        System.out.println("1. Netflix");
        System.out.println("2. Amazon Prime");
        System.out.println("3. Apple TV+");
        System.out.println("4. Crave");
        System.out.print("Enter your choice: ");

        int platform_choice;
        try {
            platform_choice = scanner.nextInt();
            scanner.nextLine();
        } catch (InputMismatchException e) {
            System.out.println("Invalid choice. Please enter a number between 1 and 4.");
            scanner.nextLine();
            return;
        }

        String platform;
        String platform_parser;
        switch (platform_choice) {
            case 1:
                platform = "Netflix";
                platform_parser = "netflix";
                break;
            case 2:
                platform = "Amazon Prime Video";
                platform_parser = "amazon-prime";
                break;
            case 3:
                platform = "Apple TV+";
                platform_parser = "apple-tv";
                break;
            case 4:
                platform = "Crave";
                platform_parser = "crave";
                break;
            default:
                System.out.println("Invalid choice. Please enter a number between 1 and 4.");
                return;
        }

        try {
            String contact_info = fetch_contact_info(platform_parser);
            System.out.println("\nContact Details for " + platform + ":");
            System.out.println(contact_info);
        } catch (Exception e) {
            System.out.println("Error fetching contact details for " + platform + ": " + e.getMessage());
        }
    }

    // Method to fetch contact information by parsing platform websites
    static String fetch_contact_info(String platform) throws Exception {
        return web_html_parser(platform);
    }

    // Method to parse HTML from platform websites to extract contact details
    static String web_html_parser(String platform) throws Exception {
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
            // Use Jsoup to fetch and parse the webpage
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36")
                    .timeout(10000)
                    .get();

            String text = doc.text();
            // Regex pattern for email addresses
            String regex_email = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}";
            Pattern email_pattern = Pattern.compile(regex_email);
            Matcher email_matcher = email_pattern.matcher(text);
            // Regex pattern for phone numbers, adjusted per platform
            String regex_phone = (platform.equals("apple-tv") || platform.equals("crave")) ?
                    "1-8\\d{2}-[A-Z0-9-]{6,8}" :
                    "\\+?\\d{0,3}[\\s-]?(\\(?\\d{3}\\)?[\\s-]?\\d{3}[\\s-]?\\d{4})";

            Pattern phone_pattern = Pattern.compile(regex_phone);
            Matcher phone_match = phone_pattern.matcher(text);

            StringBuilder contact_info = new StringBuilder();

            // Extract and append email addresses
            List<String> emails = new ArrayList<>();
            while (email_matcher.find()) {
                emails.add(email_matcher.group());
            }
            if (emails.isEmpty()) {
                contact_info.append("Email: Not found\n");
            } else {
                contact_info.append("Email(s):\n");
                for (String email : emails) {
                    contact_info.append("  - ").append(email).append("\n");
                }
            }

            // Extract and append phone numbers
            List<String> phones = new ArrayList<>();
            while (phone_match.find()) {
                phones.add(phone_match.group());
            }
            if (phones.isEmpty()) {
                contact_info.append("Phone: Not found\n");
            } else {
                contact_info.append("Phone Number(s):\n");
                for (String phone : phones) {
                    contact_info.append("  - ").append(phone).append("\n");
                }
            }

            // Append website URL if contact info was found
            if (!emails.isEmpty() || !phones.isEmpty()) {
                contact_info.append("Website: ").append(url);
            }

            return contact_info.toString();
        } catch (IOException e) {
            throw new Exception("Failed to fetch contact details due to a network or parsing error: " + e.getMessage());
        }
    }

    // Method to search for media by cast member with autocomplete suggestions
    static void search_by_cast(Scanner scanner) {
        System.out.print("Enter cast name prefix: ");
        String prefix = scanner.nextLine();
        List<String> suggestions = cast_trie.fetch_cast_suggestions(prefix);
        if (suggestions.isEmpty()) {
            System.out.println("No cast members found with prefix: " + prefix);
            return;
        }
        System.out.println("\nSuggestions:");
        suggestions.forEach(s -> System.out.println("- " + s));
        System.out.print("Select a cast member to view details (or press Enter to skip): ");
        String selected = scanner.nextLine();
        if (!selected.isEmpty()) {
            List<Media> results = cast_index.search(selected);
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
<<<<<<< HEAD
}
=======
}
>>>>>>> 02e7769 (updated commented code)
