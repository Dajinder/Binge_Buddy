Binge Buddy

A Java CLI app to browse subscription plans, movies, and TV shows from OTT platforms like Netflix, Amazon Prime, Apple TV+, and Crave, with autocomplete search features.

Features:

Subscription Plans: View/sort plans by platform or price.
Movies & TV Shows: Search by name (autocomplete), genre (typo correction), word, or platform.
Cast Search: Find media by cast with autocomplete (e.g., "Tom" suggests "Tom Hanks").
Error Handling: Graceful handling of invalid inputs.
Data Indexing: Uses Trie for autocomplete and cast index for quick lookups

Prerequisites:
Java 17+.
IDE (e.g., IntelliJ) or terminal with javac and java.
CSV files: subscription_plans.csv, Netflix_Data.csv, AmazonPrime_Data.csv, AppleTV_Data.csv, Crave_Data.csv.

Installation:
Clone: git clone https://github.com/your-username/binge-buddy.git and cd binge-buddy.
Add required CSV files to the project root.
Compile: javac Main.java.
Run: java Main.

Usage:
Run java Main to access the main menu:

    -Subscription Details: View/sort plans.
    -Movies/TV Shows: Search by name, genre, word, or platform.
    -Get More Information: Search by cast with autocomplete.

File Structure:
Main.java: Core logic.
subscription_plans.csv: Subscription plans.
Netflix_Data.csv, AmazonPrime_Data.csv, AppleTV_Data.csv: Media data.
Crave_Data.csv: (Optional) Crave data.
README.md: Documentation.

Sample Data:
subscription_plans.csv: Plan details:
    Name,Price,Resolution,Devices,Concurrent Devices,Link,Platform
    "Basic","9.99","HD","Mobile, Tablet","1","https://netflix.com","Netflix"
    "Standard","15.49","Full HD","Mobile, Tablet, TV","2","https://netflix.com","Netflix"
    "Premium","19.99","4K","All Devices","4","https://netflix.com","Netflix"
    "Monthly","5.99","HD","All Devices","1","https://amazon.com","Amazon Prime Video"

Media CSVs: Media details (e.g., type, name, genre, cast, platform).
    Type,Name,Description,Genre,Release Date,Season,Cast,Platform,URL
    "Movie","The Matrix","A sci-fi classic","Action",1999,-,"Keanu Reeves, Laurence Fishburne",Netflix,https://netflix.com/matrix
    "Movie","Spider-Man: No Way Home","A superhero adventure","Action",2021,-,"Tom Holland, Zendaya",Netflix,https://netflix.com/spiderman
    "TV Show","Silo","A dystopian sci-fi series","Sci-Fi",2023,"1 Season","Rebecca Ferguson","Apple TV+",https://appletv.com/silo


Error Handling:
Invalid inputs: "Invalid input. Please enter a number between 1 and 5."
Missing CSV files: Error message, app continues.
Invalid genres: Suggests corrections (e.g., "Comdy" suggests "Comedy").