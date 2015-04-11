(ns uncomplicate.clojurecl.examples.openclinaction.ch07
  (:require [midje.sweet :refer :all]
            [clojure.core.async :refer [<!! chan timeout <! go]]
            [uncomplicate.clojurecl
             [core :refer :all]
             [info :refer [profiling-info durations end]]]
            [vertigo.bytes :refer [direct-buffer]]))

(with-release [dev (first (devices (first (platforms))))
               ctx (context [dev])
               cqueue (command-queue ctx dev :profiling)]

  (facts
   "Listing 7.3. Page 147."
   (let [program-source
         (slurp "test/opencl/examples/openclinaction/ch07/user-event.cl")
         notifications (chan)
         follow (follow notifications)
         v (direct-buffer (* Float/BYTES 4))
         work-sizes (work-size [1])
         platform (first (platforms))]
     (with-release [cl-v (cl-buffer ctx (* 4 Float/BYTES) :write-only)
                    prog (build-program! (program-with-source ctx [program-source]))
                    user-event-kernel (kernel prog "user_event")
                    user-event (host-event ctx)
                    kernel-event (event)
                    read-event (event)]
       (facts
        (set-args! user-event-kernel cl-v) => user-event-kernel
        (enq-nd! cqueue user-event-kernel
                          work-sizes (events user-event) kernel-event)
        => cqueue
        (enq-read! cqueue cl-v v (events kernel-event) read-event) => cqueue
        (follow read-event) => notifications
        (set-status! user-event :complete) => user-event
        (:event (<!! notifications)) => read-event))))


  (facts
   "Listing 7.6. Page 155."
   (let [program-source
         (slurp "test/opencl/examples/openclinaction/ch07/profile-read.cl")
         bytesize (Math/pow 2 20)
         notifications (chan)
         follow (follow notifications)
         data (direct-buffer bytesize)
         num (int-array [(/ (long bytesize) 16)])
         num-iterations 2
         work-sizes (work-size [1])
         platform (first (platforms))]
     (with-release [cl-data (cl-buffer ctx bytesize :write-only)
                    prog (build-program! (program-with-source ctx [program-source]))
                    profile-read (kernel prog "profile_read")
                    profile-event (event)]
       (facts
        (set-args! profile-read cl-data num) => profile-read

        (enq-nd! cqueue profile-read work-sizes)
        (enq-read! cqueue cl-data data profile-event)
        (follow profile-event)

        (< 10000
           (-> (<!! notifications) :event profiling-info durations :end)
           300000)
        => true))))

  (facts
   "Listing 7.7. Page 157."
   (let [program-source
         (slurp "test/opencl/examples/openclinaction/ch07/profile-items.cl")
         num-ints 65536
         data (int-array (range num-ints))
         notifications (chan)
         follow (follow notifications)
         work-sizes (work-size [512] [1])
         platform (first (platforms))]
     (with-release [cl-x (cl-buffer ctx (* num-ints Integer/BYTES 4) :write-only)
                    prog (build-program! (program-with-source ctx [program-source]))
                    profile-items (kernel prog "profile_items")
                    profile-event (event)]
       (facts
        (set-args! profile-items cl-x (int-array [num-ints])) => profile-items
        (enq-nd! cqueue profile-items work-sizes nil profile-event)
        (follow profile-event)

        (< 10000
           (-> (<!! notifications) :event profiling-info durations :end)
           300000)
        => true)))))
