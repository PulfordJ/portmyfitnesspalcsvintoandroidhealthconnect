# Port MyFitnessPal CSV to Health Connect

An Android app that allows users to import weight data exported from MyFitnessPal (in CSV format) into Google Health Connect.

## Features

* Requests necessary Health Connect permissions.
* Lets users pick a CSV file exported from MyFitnessPal.
* Parses the weight and date data.
* Inserts weight records into Health Connect with `manualEntry` metadata.

## How to Use

1. Export your weight data from MyFitnessPal's website.
2. Save the CSV file to your Android device.
3. Open this app and grant Health Connect permissions.
4. Tap **Select CSV File** to choose your CSV.
5. Tap **Import Weights** to send the data to Health Connect.

## CSV Format

The app expects the following CSV header:

```
Date,Body Fat %,Weight
```

Example row:

```
2024-01-01,18.5,75.3
```

## Permissions

* `WRITE_WEIGHT` permission is required for Health Connect.

## Requirements

* Android with Health Connect installed.
* CSV file with correct format.

## License

MIT License
