import java.io.*;
import java.util.*;

// Main class for the OTT platform CLI project
public class Main {
    // Data structures to store subscription plans and media items
    static List<SubscriptionPlan> plans = new ArrayList<>();
    static List<Media> mediaList = new ArrayList<>();
    static Map<String, Integer> wordFrequency = new HashMap<>(); // Tracks word search frequency
    static Trie trie = new Trie(); // Trie for word completion
    static Trie castTrie = new Trie(); // For cast name suggestions

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

        // Build the cast index and populate castTrie
        static void build() {
            castIndex.clear(); // Clear existing entries to prevent duplicates
            Set<String> uniqueCastNames = new HashSet<>(); // To avoid duplicate cast names in trie
            for (Media media : mediaList) {
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
                    // Add original actor name to castTrie (before normalization)
                    String originalActor = actor.trim();
                    if (!originalActor.isEmpty() && !uniqueCastNames.contains(originalActor)) {
                        castTrie.insertCast(originalActor);
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
        // Load data from CSV files
        loadSubscriptionPlans("subscription_plans.csv");
        loadMediaData(new String[]{"Netflix_Data.csv", "AmazonPrime_Data.csv", "AppleTV_Data.csv"});
        CastIndex.build(); // Build the cast index after loading media data
        

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
            choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            switch (choice) {
                case 1: showSubscriptionMenu(scanner); break;
                case 2: showMediaMenu(scanner, "Movie"); break;
                case 3: showMediaMenu(scanner, "TV Show"); break;
                case 4: showMoreInformationMenu(scanner); break;
                case 5: System.out.println("Exiting program..."); break;
                default: System.out.println("Invalid choice. Please try again.");
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
                        mediaList.add(media);
                        trie.insert(media.name, media); // Insert Media object into Trie
                    }
                }
            } catch (IOException e) {
                System.out.println("Error reading " + file + ": " + e.getMessage());
            }
        }
    }

    // Integrated buildCastIndex method
    // static void buildCastIndex() {
    //     castIndex.clear();
    //     String[] CSV_FILES = {"Netflix_Data.csv", "AmazonPrime_Data.csv", "AppleTV_Data.csv"};
    //     for (String csvFile : CSV_FILES) {
    //         try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
    //             br.readLine();
    //             String line;
    //             while ((line = br.readLine()) != null) {
    //                 String[] values = splitCSV(line); // Use existing splitCSV method
    //                 if (values.length >= 8) {
    //                     String type = values[0].trim();
    //                     String name = values[1].trim();
    //                     String description = values[2].trim();
    //                     String genre = values[3].trim();
    //                     String releaseDate = values[4].trim();
    //                     String season = values.length > 5 ? values[5].trim() : "-";
    //                     String cast = values.length > 6 ? values[6].trim() : values[5].trim();
    //                     String platform = values.length > 7 ? values[7].trim() : values[6].trim();
    //                     String url = values.length > 8 ? values[8].trim() : values[7].trim();

    //                     Media media = new Media(type, name, description, genre, releaseDate, season, cast, platform, url);

    //                     for (String actor : cast.split(",")) {
    //                         actor = actor.trim()
    //                                      .replaceAll("^\"|\"$", "")
    //                                      .replaceAll("\\p{Zs}+", " ")
    //                                      .replaceAll("[^\\p{ASCII}]", "")
    //                                      .toLowerCase();
    //                         castIndex.putIfAbsent(actor, new ArrayList<>());
    //                         castIndex.get(actor).add(media);
    //                     }
    //                 }
    //             }
    //         } catch (IOException e) {
    //             System.out.println("Error reading file: " + csvFile);
    //         }
    //     }
    // }

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
            subChoice = scanner.nextInt();
            scanner.nextLine();

            switch (subChoice) {
                case 1: showPlatformDetails("Netflix"); break;
                case 2: showPlatformDetails("Amazon Prime Video"); break;
                case 3: showPlatformDetails("Apple TV+"); break;
                case 4: showPlatformDetails("Crave"); break;
                case 5: quickSort(plans, 0, plans.size() - 1, true); displaySortedPlans(); break;
                case 6: quickSort(plans, 0, plans.size() - 1, false); displaySortedPlans(); break;
                case 7: break;
                default: System.out.println("Invalid choice.");
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
            System.out.println("1. Search by Name (with word completion)");
            System.out.println("2. Search by Genre");
            System.out.println("3. Search by Word (in name/description)");
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
            mediaList.stream()
                    .filter(m -> m.type.equals(type) && m.name.equalsIgnoreCase(selected))
                    .forEach(m -> System.out.println(m + "\n------------------------"));
        }
    }

    // Search by genre (unchanged)
    static void searchByGenre(Scanner scanner, String type) {
        System.out.println("Available Genres: Comedy, Thriller, Animation, Action, Others");
        System.out.print("Enter genre: ");
        String genre = scanner.nextLine();
        String matchedGenre = findClosestGenre(genre);
        if (!isValidGenre(genre)) {
            System.out.print("Did you mean " + matchedGenre + "? (y/n): ");
            if (!scanner.nextLine().equalsIgnoreCase("y")) return;
            genre = matchedGenre;
        }
        final String finalGenre = genre;
        mediaList.stream()
                .filter(m -> m.type.equals(type) && mapToValidGenre(m.genre).equalsIgnoreCase(finalGenre))
                .forEach(m -> System.out.println(m + "\n------------------------"));
    }

    static boolean isValidGenre(String genre) {
        String[] VALID_GENRES = {"Comedy", "Thriller", "Animation", "Action", "Others"};
        return Arrays.stream(VALID_GENRES).anyMatch(g -> g.equalsIgnoreCase(genre));
    }

    static String findClosestGenre(String input) {
        String[] VALID_GENRES = {"Comedy", "Thriller", "Animation", "Action", "Others"};
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
        String[] VALID_GENRES = {"Comedy", "Thriller", "Animation", "Action"};
        return Arrays.stream(VALID_GENRES).anyMatch(g -> g.equalsIgnoreCase(genre)) ? genre : "Others";
    }

    // Search by word (unchanged)
    // static void searchByWord(Scanner scanner, String type) {
    //     System.out.print("Enter word to search in " + type + " name/description: ");
    //     String word = scanner.nextLine().toLowerCase();
    //     wordFrequency.put(word, wordFrequency.getOrDefault(word, 0) + 1);
    //     mediaList.stream()
    //             .filter(m -> m.type.equals(type) && (m.name.toLowerCase().contains(word) || m.description.toLowerCase().contains(word)))
    //             .forEach(m -> System.out.println(m + "\n------------------------"));
    //     System.out.println("Search frequency for '" + word + "': " + wordFrequency.get(word));
    // }

    static void searchByWord(Scanner scanner, String type) {
        System.out.print("Enter word to search in " + type + " name/description: ");
        String word = scanner.nextLine().toLowerCase().trim();
        wordFrequency.put(word, wordFrequency.getOrDefault(word, 0) + 1);

        // Filter media items where the exact word appears in name or description
        boolean found = false;
        for (Media m : mediaList) {
            if (!m.type.equals(type)) continue;

            // Split name and description into words (using whitespace and punctuation as delimiters)
            String[] nameWords = m.name.toLowerCase().split("[\\s.,!?]+");
            String[] descWords = m.description.toLowerCase().split("[\\s.,!?]+");

            // Check if the search word matches any whole word
            boolean matchInName = Arrays.stream(nameWords).anyMatch(w -> w.equals(word));
            boolean matchInDesc = Arrays.stream(descWords).anyMatch(w -> w.equals(word));

            if (matchInName || matchInDesc) {
                System.out.println(m + "\n------------------------");
                found = true;
            }
        }

        if (!found) {
            System.out.println("No " + type + "s found containing the word '" + word + "'.");
        }
        System.out.println("Search frequency for '" + word + "': " + wordFrequency.get(word));
    }

    // Show all media for a platform (unchanged)
    static void showPlatformMedia(String platform, String type) {
        System.out.println("\nAll " + type + "s on " + platform + ":");
        mediaList.stream()
                .filter(m -> m.type.equals(type) && m.platform.equalsIgnoreCase(platform))
                .forEach(m -> System.out.println(m + "\n------------------------"));
    }

    // More Information menu (updated to use CastIndex)
    static void showMoreInformationMenu(Scanner scanner) {
        int choice;
        do {
            System.out.println("\n=== More Information Menu ===");
            System.out.println("1. Search by Cast");
            System.out.println("2. Back to Main Menu");
            System.out.print("Enter your choice: ");

            try {
                choice = scanner.nextInt();
                scanner.nextLine();
            } catch (InputMismatchException e) {
                System.out.println("Invalid choice. Please enter a number between 1 and 2.");
                scanner.nextLine();
                choice = -1;
            }

            switch (choice) {
                case 1: searchByCast(scanner); break;
                case 2: System.out.println("Returning to Main Menu..."); break;
                default:
                    if (choice != -1) {
                        System.out.println("Invalid choice. Please enter a number between 1 and 2.");
                    }
            }
        } while (choice != 2);
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
        System.out.print("Enter cast name prefix (e.g., 'Tom' for Tom Hanks): ");
        String prefix = scanner.nextLine();
        List<String> suggestions = castTrie.getCastSuggestions(prefix);
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
}