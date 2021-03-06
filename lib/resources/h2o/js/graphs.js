function zip(x, y) {
    return $.map(x, function (el, idx) {
      return [[el, y[idx]]];
    }); 
}
/** Name of element to append SVG, names (y-axis), values (x-axis) */
function g_varimp(divid, names, varimp) { 
  // Create a dataset as an array of tuples
  var dataset = zip(names, varimp);
  var valueLabelWidth = 20; // Space reserved for values on the right
  var barLabelWidth = 80; // Space reserved for col names on the left
  var barHeight = 20; // Height of bar
  var maxBarWidth = 640; // Maximum bar width
  // Setup size and axis
  var margin = {top: 30, right: 5, bottom: 10, left: 5},
      width = maxBarWidth - margin.left - margin.right,
      height = names.length*barHeight - margin.top - margin.bottom;

  var xScale = d3.scale.linear()
      .range([0, width])
      .domain(d3.extent(varimp)).nice();
        
  var yScale = d3.scale.ordinal()
      .rangeRoundBands([0, height], .2)
      .domain(names);

  var xAxis = d3.svg.axis()
      .scale(xScale)
      .orient("top");

  var svg = d3.select("#"+divid).append("svg")
    .attr("width", width + margin.left + margin.right + valueLabelWidth + barLabelWidth)
    .attr("height", height + margin.top + margin.bottom)

    /*
  var tooltip = d3.select("body")
                  .append("div")
                  .attr("id", "d3tip")
                  .classed("hidden", true); */

  var fx = function(d) { return xScale(Math.min(0, d[1])); }
  var fy = function(d) { return yScale(d[0]); }
  var yText = function(d, i) { return fy(d) + yScale.rangeBand() / 2; };
  var barLabel = function(d) { return d[0]; };

  var labelsContainer = svg.append("g").attr("transform", "translate("+(barLabelWidth-margin.left) + "," + margin.top + ")");
  labelsContainer.selectAll("text").data(dataset).enter().append('text')
    .attr('y', yText)
    .attr('stroke', 'none')
    .attr('fill', 'black')
    .attr("dy", ".25em") // vertical-align: middle
    .attr('text-anchor', 'end')
    .attr("font-size", "11px")
    .text(barLabel);

  var barsContainer = svg.append("g").attr("transform", "translate(" + barLabelWidth + "," + margin.top + ")");
  barsContainer.selectAll(".bar")
      .data(dataset)
    .enter().append("rect")
      .attr("class", function(d) { return d[1] < 0 ? "bar negative" : "bar positive"; })
      .attr("x", fx)
      .attr("y", fy)
      .attr("width", function(d) { return Math.abs(xScale(d[1]) - xScale(0)); })
      .attr("height", yScale.rangeBand());
/*
      .on("mouseover", function (d) {
        var xPosition = width  + document.getElementById(divid).offsetLeft;
        var yPosition = parseFloat(d3.select(this).attr("y")) + yScale.rangeBand() / 2 + document.getElementById(divid).offsetTop;
        tooltip.style("left", xPosition + "px")
                .style("top", yPosition + "px");
        tooltip.html("<p>" + d[0] + "<br/>" + d[1] + "</p>");
        tooltip.classed("hidden", false);
        })
      .on("mouseout", function(d) {
        tooltip.classed("hidden", true);
        });*/

  var dname = function(d) { return d[0]; };
  var dval  = function(d) { return d[1]; };
  var tval  = function(d) { return d3.round(dval(d), 2); };

  barsContainer.selectAll("text").data(dataset).enter().append("text")
    .attr("x", function(d) { return xScale(dval(d)); })
    .attr("y", yText)
    .attr("dx", 3) // padding-left
    .attr("dy", ".35em") // vertical-align: middle
    .attr("text-anchor", "start") // text-align: right
    .attr("fill", "black")
    .attr("stroke", "none")
    .attr("font-size", "11px")
    .text(function(d) { return tval(d); });

  // Axes into a dedicated container
  var gridContainer = svg.append("g").attr("transform", "translate(" + barLabelWidth + "," + margin.top + ")");
  gridContainer.append("g")
      .attr("class", "x axis")
      .call(xAxis);

  gridContainer.append("g")
      .attr("class", "y axis")
    .append("line")
      .attr("x1", xScale(0))
      .attr("x2", xScale(0))
      .attr("y2", height);

  // Hook for sort button
  $(document).ready(function() {
      var sorted = false;
      var sortIt = function() {
          sorted = !sorted;
          bars = function(l,r) {
              if (sorted) {
                  return l[1]- r[1];
              }
              return r[1]- l[1];
          }
          barsContainer.selectAll(".bar")
              .sort(bars)
              .transition()
              .delay(function(d,i) { return i * 40;})
              .duration(400)
              .attr("y", function(d,i) {
                  return yScale(names[i]);
              });
          barsContainer.selectAll("text")
              .sort(bars)
              .transition()
              .delay(function(d,i) { return i * 40;})
              .duration(400)
              .text(function(d) { return tval(d); })
              .attr("text-anchor", "start") // text-align: right
              .attr("x", function(d,i) { return xScale(dval(d)); })
              .attr("y", function(d,i) { return yScale(names[i]) + yScale.rangeBand() / 2; } );
          labelsContainer.selectAll("text")
              .sort(bars)
              .transition()
              .delay(function(d,i) { return i * 40;})
              .duration(400)
              .text(function(d,i) { return d[0]; })
              .attr("y", function(d,i) { return yScale(names[i]) + yScale.rangeBand() / 2; } );
      };
      d3.select("#sortBars").on("click", sortIt);
  });
}

