import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.chain.ChainMapper;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UnitMultiplication {

    public static class TransitionMapper extends Mapper<Object, Text, Text, Text> {

        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

            //input format: fromPage\t toPage1,toPage2,toPage3
            //target: build transition matrix  unit-> fromPage\t toPage=probability
            String[] line = value.toString().trim().split("\t");
            if (line.length != 2 || line[1].trim().equals("")){
                return;
            }
            String from = line[0];
            String[] to = line[1].trim().split(",");
            for (String t : to){
                context.write(new Text(from), new Text(t + "=" + (double) 1 / to.length));
            }
        }
    }

    public static class PRMapper extends Mapper<Object, Text, Text, Text> {

        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

            //input format: Page\t PageRank
            //target: write to reducer
            String[] prline = value.toString().trim().split("\t");
            context.write(new Text(prline[0]), new Text(prline[1]));
        }
    }

    public static class MultiplicationReducer extends Reducer<Text, Text, Text, Text> {


        @Override
        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {

            //input key = fromPage value=<toPage=probability..., pageRank>
            //target: get the unit multiplication
            // Example: key: a  value: <b=1/3,c=1/3,d=1/3,1/4>
            List<String> list = new ArrayList<String>();
            double rank = 0;
            for (Text val : values){
                String sval = val.toString().trim();
                if (sval.contains("=")){
                    list.add(sval);
                } else {
                    rank = Double.parseDouble(sval);
                }
            }
            for (String l : list){
                String[] s = l.split("=");
                String outputkey = s[0];
                String outputval = String.valueOf(Double.parseDouble(s[1]) * rank);
                context.write(new Text(outputkey), new Text(outputval));
            }
        }
    }

    public static void main(String[] args) throws Exception {

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf);
        job.setJarByClass(UnitMultiplication.class);

        //how chain two mapper classes?
        ChainMapper.addMapper(job, TransitionMapper.class, Object.class, Text.class, Text.class, Text.class, conf);
        ChainMapper.addMapper(job, PRMapper.class, Object.class, Text.class, Text.class, Text.class, conf);

        job.setReducerClass(MultiplicationReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        MultipleInputs.addInputPath(job, new Path(args[0]), TextInputFormat.class, TransitionMapper.class);
        MultipleInputs.addInputPath(job, new Path(args[1]), TextInputFormat.class, PRMapper.class);

        FileOutputFormat.setOutputPath(job, new Path(args[2]));
        job.waitForCompletion(true);
    }

}
