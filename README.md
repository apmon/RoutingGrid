RoutingGrid
===========

Creates a distance routing grid between cities to check for broken routes in OSM

This is a little java program to create a grid of distances between cities / locations as seen by OSM data.
It uses the router provided by map.projct-osrm.org.

It also uses a refernece source to determin which routes are longer than expected. For the moment it uses
the google directions API as a reference.

To run the program you need the following command line:

java -jar RoutingGrid.jar cities.list output_grid.html

This uses the file cities.list as the input list of cities between which to create the routing matrix.
output_grid.html is the output file it generates.


As the reference source for the distances is likely not to change often, it can be cached in a reference list.

java -jar RoutingGrid.jar cities.list output_grid.html reference.list

To initially create the reference list use:

java -jar RoutingGrid.jar cities.list output_grid.html reference.list 1


 