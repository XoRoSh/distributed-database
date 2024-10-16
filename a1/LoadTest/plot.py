import matplotlib.pyplot as plt

# Simulated data: SECONDS at significant points
events = ['Start', 'After 1st Command', 'After 2nd Command', 'After 3rd Command', 'After 4th Command', 'End']
time_in_seconds = [0, 12, 25, 38, 52, 70]

# Create the plot
plt.figure(figsize=(10, 5))
plt.plot(events, time_in_seconds, marker='o')

# Labels and title
plt.title("Execution Time for LoadTest Commands")
plt.xlabel("Event")
plt.ylabel("Time (seconds)")

# Display the graph
plt.grid(True)
plt.show()